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
    @DisplayName("인앱 브라우저를 빠져나왔는데 앱이 열리지 않은 경우 스토어로 보내지 않고 재시도 페이지를 준다.")
    void escapedButAppNotOpened() throws Exception {
        mockMvc.perform(get("/invite")
                        .param("code", "ABC123")
                        .param("from", "kakao")
                        .header(HttpHeaders.USER_AGENT, IOS_UA))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("https://" + INVITE_HOST + "/invite?code=ABC123")));
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
