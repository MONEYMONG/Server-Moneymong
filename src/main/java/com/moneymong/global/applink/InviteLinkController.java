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
            @RequestParam(value = ESCAPED_PARAM, required = false) String from,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent
    ) {
        boolean ios = isIos(userAgent);
        if (!isValidCode(code)) {
            return redirectToStore(ios);
        }

        if (isKakaoTalk(userAgent)) {
            return appLaunchPage(ios ? externalBrowserUri(code) : androidIntentUri(code), ios);
        }

        // 인앱 브라우저를 빠져나왔는데도 앱이 열리지 않은 경우. 직접 실행할 수단을 준다.
        if (ESCAPED_VALUE.equals(from)) {
            return appLaunchPage(inviteUrl(code), ios);
        }

        return redirectToStore(ios);
    }

    /**
     * 카카오톡 인앱 브라우저에서 외부 브라우저로 초대 링크를 넘긴다.
     * iOS가 유니버설 링크를 가로채 앱을 실행하고, 앱이 없으면 Safari가 링크를 연다.
     */
    private String externalBrowserUri(String code) {
        String target = inviteUrl(code) + "&" + ESCAPED_PARAM + "=" + ESCAPED_VALUE;
        return "kakaotalk://web/openExternal?url=" + encode(target);
    }

    /**
     * 앱이 없으면 browser_fallback_url로 플레이스토어가 열리므로 설치 여부를 서버가 판단하지 않는다.
     */
    private String androidIntentUri(String code) {
        return "intent://" + inviteHost + "/invite?code=" + code
                + "#Intent;scheme=https;package=" + androidPackage
                + ";S.browser_fallback_url=" + encode(playStoreUri.toString())
                + ";end";
    }

    private String inviteUrl(String code) {
        return "https://" + inviteHost + "/invite?code=" + code;
    }

    private ResponseEntity<String> redirectToStore(boolean ios) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(ios ? appStoreUri : playStoreUri)
                .build();
    }

    private ResponseEntity<String> appLaunchPage(String target, boolean ios) {
        String storeUrl = (ios ? appStoreUri : playStoreUri).toString();
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .cacheControl(CacheControl.noStore())
                .body(renderPage(target, storeUrl));
    }

    private String renderPage(String target, String storeUrl) {
        String escapedTarget = HtmlUtils.htmlEscape(target);
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
                """.formatted(escapedTarget, HtmlUtils.htmlEscape(storeUrl), escapedTarget);
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
