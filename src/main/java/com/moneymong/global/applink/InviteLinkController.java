package com.moneymong.global.applink;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

@RestController
public class InviteLinkController {

    /**
     * 초대 코드는 영숫자로만 구성된다. HTML/JS에 그대로 삽입하므로 이 검증이 XSS 방어를 겸한다.
     */
    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Za-z0-9]{1,32}$");

    /**
     * 클라이언트가 초대 링크에 붙이는 소속 식별자. iOS는 agencyID, Android는 agencyId로 이름이 다르다.
     * 앱이 둘 중 자기가 아는 이름만 읽으므로 서버는 두 이름을 모두 붙여서 넘긴다.
     */
    private static final Pattern AGENCY_ID_PATTERN = Pattern.compile("^[1-9][0-9]{0,18}$");
    private static final String AGENCY_ID_IOS = "agencyID";
    private static final String AGENCY_ID_ANDROID = "agencyId";

    /**
     * 카카오톡 인앱 브라우저를 한 번 빠져나온 요청임을 표시한다.
     * 이 표시가 있으면 스토어로 보내지 않고 앱 실행을 다시 시도할 수 있는 페이지를 준다.
     */
    private static final String ESCAPED_PARAM = "from";
    private static final String ESCAPED_VALUE = "kakao";

    private final URI appStoreUri;
    private final URI playStoreUri;
    private final String inviteHost;
    private final String androidPackage;

    public InviteLinkController(
            @Value("${applink.app-store-url}") String appStoreUrl,
            @Value("${applink.play-store-url}") String playStoreUrl,
            @Value("${applink.invite-host}") String inviteHost,
            @Value("${applink.android-package}") String androidPackage
    ) {
        this.appStoreUri = URI.create(appStoreUrl);
        this.playStoreUri = URI.create(playStoreUrl);
        this.inviteHost = inviteHost;
        this.androidPackage = androidPackage;
    }

    /**
     * 앱이 설치된 기기에서 유니버설 링크/앱 링크로 열리면 이 엔드포인트까지 오지 않는다.
     * 여기까지 오는 경우는 두 가지다.
     * 1. 앱이 없는 브라우저: 스토어로 보낸다.
     * 2. 카카오톡 인앱 브라우저: 앱 설치 여부와 무관하게 링크를 웹뷰에서 직접 열기 때문에 도달한다.
     *    이때는 스토어로 보내면 앱이 있어도 스토어가 열리므로, 외부 브라우저(iOS)나
     *    앱(Android)으로 넘기는 페이지를 준다.
     */
    @GetMapping("/invite")
    public ResponseEntity<String> invite(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = AGENCY_ID_IOS, required = false) String agencyIdFromIos,
            @RequestParam(value = AGENCY_ID_ANDROID, required = false) String agencyIdFromAndroid,
            @RequestParam(value = ESCAPED_PARAM, required = false) String from,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent
    ) {
        boolean ios = isIos(userAgent);
        String agencyId = firstValidAgencyId(agencyIdFromIos, agencyIdFromAndroid);
        if (!isValidCode(code)) {
            return redirectToStore(ios);
        }

        if (isKakaoTalk(userAgent)) {
            String target = ios ? externalBrowserUri(code, agencyId) : androidIntentUri(code, agencyId);
            return appLaunchPage(target, ios);
        }

        // 인앱 브라우저를 빠져나왔는데도 앱이 열리지 않은 경우. 초대 코드를 직접 입력할 수 있게 안내한다.
        if (ESCAPED_VALUE.equals(from)) {
            return fallbackPage(code, ios);
        }

        return redirectToStore(ios);
    }

    /**
     * 카카오톡 인앱 브라우저에서 외부 브라우저로 초대 링크를 넘긴다.
     * iOS가 유니버설 링크를 가로채 앱을 실행하고, 앱이 없으면 Safari가 링크를 연다.
     */
    private String externalBrowserUri(String code, String agencyId) {
        String target = inviteUrl(code, agencyId) + "&" + ESCAPED_PARAM + "=" + ESCAPED_VALUE;
        return "kakaotalk://web/openExternal?url=" + encode(target);
    }

    /**
     * 앱이 없으면 browser_fallback_url로 플레이스토어가 열리므로 설치 여부를 서버가 판단하지 않는다.
     */
    private String androidIntentUri(String code, String agencyId) {
        return "intent://" + inviteHost + "/invite" + query(code, agencyId)
                + "#Intent;scheme=https;package=" + androidPackage
                + ";S.browser_fallback_url=" + encode(playStoreUri.toString())
                + ";end";
    }

    private String inviteUrl(String code, String agencyId) {
        return "https://" + inviteHost + "/invite" + query(code, agencyId);
    }

    /**
     * 앱이 딥링크를 받아들이려면 code와 소속 식별자가 모두 있어야 한다. 식별자는 두 이름으로 함께 넘긴다.
     */
    private String query(String code, String agencyId) {
        StringBuilder query = new StringBuilder("?code=").append(code);
        if (agencyId != null) {
            query.append("&").append(AGENCY_ID_ANDROID).append("=").append(agencyId)
                    .append("&").append(AGENCY_ID_IOS).append("=").append(agencyId);
        }
        return query.toString();
    }

    private String firstValidAgencyId(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && AGENCY_ID_PATTERN.matcher(candidate).matches()) {
                return candidate;
            }
        }
        return null;
    }

    private ResponseEntity<String> redirectToStore(boolean ios) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(ios ? appStoreUri : playStoreUri)
                .build();
    }

    /**
     * 외부 브라우저까지 왔는데 앱이 열리지 않은 상태다. 여기서 앱을 여는 링크는 넣지 않는다.
     * iOS는 같은 도메인의 유니버설 링크로는 앱을 열지 않고, 스크립트로 넘기는 이동은
     * 사용자 조작으로 보지 않아 역시 앱이 열리지 않는다. 남은 수단은 초대 코드 직접 입력이다.
     */
    private ResponseEntity<String> fallbackPage(String code, boolean ios) {
        String storeUrl = (ios ? appStoreUri : playStoreUri).toString();
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .cacheControl(CacheControl.noStore())
                .body(renderFallbackPage(code, storeUrl));
    }

    private ResponseEntity<String> appLaunchPage(String target, boolean ios) {
        String storeUrl = (ios ? appStoreUri : playStoreUri).toString();
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .cacheControl(CacheControl.noStore())
                .body(renderPage(target, storeUrl));
    }

    private String renderPage(String target, String storeUrl) {
        // href는 HTML escape가 필요하고(&가 &amp;로), script 안에서는 escape하면 URL이 깨진다.
        String hrefTarget = HtmlUtils.htmlEscape(target);
        String scriptTarget = target.replace("\\", "\\\\").replace("\"", "\\\"").replace("</", "<\\/");
        return """
                <!doctype html>
                <html lang="ko">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>머니몽 초대</title>
                <style>
                body { margin: 0; font-family: -apple-system, BlinkMacSystemFont, "Apple SD Gothic Neo", sans-serif;
                       display: flex; align-items: center; justify-content: center; min-height: 100vh; background: #fff; }
                main { text-align: center; padding: 24px; }
                p { color: #555; font-size: 15px; margin: 0 0 20px; }
                a { display: block; padding: 14px 20px; border-radius: 10px; text-decoration: none; font-size: 16px; }
                .primary { background: #3B82F6; color: #fff; font-weight: 600; }
                .secondary { color: #888; font-size: 14px; margin-top: 12px; }
                </style>
                </head>
                <body>
                <main>
                <p>머니몽 앱으로 이동합니다.<br>화면이 그대로면 아래 버튼을 눌러주세요.</p>
                <a class="primary" href="%s">앱에서 열기</a>
                <a class="secondary" href="%s">앱 설치하기</a>
                </main>
                <script>
                location.href = "%s";
                </script>
                </body>
                </html>
                """.formatted(hrefTarget, HtmlUtils.htmlEscape(storeUrl), scriptTarget);
    }

    private String renderFallbackPage(String code, String storeUrl) {
        return """
                <!doctype html>
                <html lang="ko">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>머니몽 초대</title>
                <style>
                body { margin: 0; font-family: -apple-system, BlinkMacSystemFont, "Apple SD Gothic Neo", sans-serif;
                       display: flex; align-items: center; justify-content: center; min-height: 100vh; background: #fff; }
                main { text-align: center; padding: 24px; }
                p { color: #555; font-size: 15px; margin: 0 0 16px; }
                .code { font-size: 32px; font-weight: 700; letter-spacing: 4px; color: #111; margin-bottom: 24px; }
                a { display: block; padding: 14px 20px; border-radius: 10px; text-decoration: none;
                    font-size: 16px; background: #3B82F6; color: #fff; font-weight: 600; }
                </style>
                </head>
                <body>
                <main>
                <p>머니몽 앱에서 아래 초대 코드를 입력해주세요.</p>
                <div class="code">%s</div>
                <a href="%s">앱 설치하기</a>
                </main>
                </body>
                </html>
                """.formatted(HtmlUtils.htmlEscape(code), HtmlUtils.htmlEscape(storeUrl));
    }

    private boolean isValidCode(String code) {
        return code != null && CODE_PATTERN.matcher(code).matches();
    }

    private boolean isKakaoTalk(String userAgent) {
        return userAgent != null && userAgent.toLowerCase().contains("kakaotalk");
    }

    private boolean isIos(String userAgent) {
        if (userAgent == null) {
            return false;
        }
        String lowerCase = userAgent.toLowerCase();
        return lowerCase.contains("iphone") || lowerCase.contains("ipad") || lowerCase.contains("ipod");
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
