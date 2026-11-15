package com.moneymong.global.applink;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class InviteLinkControllerTest {

    private static final String APP_STORE_URL = "https://apps.apple.com/kr/app/id6503661220";
    private static final String PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.moneymong.moneymong.live";
    private static final String INVITE_HOST = "prod.moneymong.site";
    private static final String ANDROID_PACKAGE = "com.moneymong.moneymong.live";

    private static final String IOS_UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148";
    private static final String ANDROID_UA = "Mozilla/5.0 (Linux; Android 14; SM-S921N) AppleWebKit/537.36 Chrome/124.0.0.0 Mobile Safari/537.36";
    private static final String KAKAO_IOS_UA = IOS_UA + " KAKAOTALK 10.5.0";
    private static final String KAKAO_ANDROID_UA = ANDROID_UA + " KAKAOTALK/10.5.0";

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new InviteLinkController(APP_STORE_URL, PLAY_STORE_URL, INVITE_HOST, ANDROID_PACKAGE))
            .build();

    @Test
    @DisplayName("카카오톡 iOS 인앱 브라우저는 외부 브라우저로 초대 링크를 넘기는 페이지를 받는다.")
    void kakaoIos() throws Exception {
        mockMvc.perform(get("/invite").param("code", "ABC123").header(HttpHeaders.USER_AGENT, KAKAO_IOS_UA))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("kakaotalk://web/openExternal?url=")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("from%3Dkakao")));
    }

    @Test
    @DisplayName("카카오톡 Android 인앱 브라우저는 intent 스킴으로 앱을 실행하는 페이지를 받는다.")
    void kakaoAndroid() throws Exception {
        mockMvc.perform(get("/invite").param("code", "ABC123").header(HttpHeaders.USER_AGENT, KAKAO_ANDROID_UA))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("intent://" + INVITE_HOST + "/invite?code=ABC123")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("package=" + ANDROID_PACKAGE)))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("S.browser_fallback_url=")));
    }

    @Test
    @DisplayName("일반 브라우저는 OS에 맞는 스토어로 리다이렉트된다.")
    void normalBrowser() throws Exception {
        mockMvc.perform(get("/invite").param("code", "ABC123").header(HttpHeaders.USER_AGENT, IOS_UA))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, APP_STORE_URL));

        mockMvc.perform(get("/invite").param("code", "ABC123").header(HttpHeaders.USER_AGENT, ANDROID_UA))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, PLAY_STORE_URL));
    }

    @Test
    @DisplayName("인앱 브라우저를 빠져나왔는데 앱이 열리지 않으면 초대 코드 입력을 안내한다.")
    void escapedButAppNotOpened() throws Exception {
        String body = mockMvc.perform(get("/invite")
                        .param("code", "123456")
                        .param("from", "kakao")
                        .header(HttpHeaders.USER_AGENT, IOS_UA))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body).contains("123456");
        org.assertj.core.api.Assertions.assertThat(body).contains(APP_STORE_URL);
        // 같은 도메인 유니버설 링크와 스크립트 이동은 앱을 열지 못하므로 넣지 않는다.
        org.assertj.core.api.Assertions.assertThat(body).doesNotContain("location.href");
        org.assertj.core.api.Assertions.assertThat(body).doesNotContain("/invite?code=");
    }

    @Test
    @DisplayName("소속 식별자는 iOS/Android 두 이름으로 함께 전달된다.")
    void agencyIdIsCarriedWithBothNames() throws Exception {
        // iOS가 만든 링크(agencyID)로 들어온 경우
        mockMvc.perform(get("/invite")
                        .param("code", "123456")
                        .param("agencyID", "77")
                        .header(HttpHeaders.USER_AGENT, KAKAO_ANDROID_UA))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("agencyId=77")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("agencyID=77")));

        // Android가 만든 링크(agencyId)로 들어온 경우
        mockMvc.perform(get("/invite")
                        .param("code", "123456")
                        .param("agencyId", "77")
                        .header(HttpHeaders.USER_AGENT, KAKAO_IOS_UA))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("agencyId%3D77")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("agencyID%3D77")));
    }

    @Test
    @DisplayName("script 안의 URL은 HTML escape되지 않고, href 속성은 escape된다.")
    void escapingDiffersBetweenHrefAndScript() throws Exception {
        String body = mockMvc.perform(get("/invite")
                        .param("code", "123456")
                        .param("agencyId", "77")
                        .header(HttpHeaders.USER_AGENT, KAKAO_ANDROID_UA))
                .andReturn().getResponse().getContentAsString();

        String script = body.substring(body.indexOf("<script>"));
        org.assertj.core.api.Assertions.assertThat(script).doesNotContain("&amp;");
        String href = body.substring(body.indexOf("class=\"primary\""), body.indexOf("<script>"));
        org.assertj.core.api.Assertions.assertThat(href).contains("&amp;");
    }

    @Test
    @DisplayName("소속 식별자가 양의 정수가 아니면 붙이지 않는다.")
    void invalidAgencyIdIsDropped() throws Exception {
        mockMvc.perform(get("/invite")
                        .param("code", "123456")
                        .param("agencyId", "0")
                        .header(HttpHeaders.USER_AGENT, KAKAO_ANDROID_UA))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("agencyId=0"))));
    }

    @Test
    @DisplayName("초대 코드가 없거나 영숫자 형식이 아니면 스토어로 리다이렉트된다.")
    void invalidCode() throws Exception {
        mockMvc.perform(get("/invite").header(HttpHeaders.USER_AGENT, KAKAO_IOS_UA))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, APP_STORE_URL));

        mockMvc.perform(get("/invite")
                        .param("code", "\"><script>alert(1)</script>")
                        .header(HttpHeaders.USER_AGENT, KAKAO_IOS_UA))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, APP_STORE_URL));
    }
}
