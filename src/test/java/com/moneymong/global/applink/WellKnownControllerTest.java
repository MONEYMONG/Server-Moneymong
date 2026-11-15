package com.moneymong.global.applink;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moneymong.support.ControllerTestSupport;
import com.moneymong.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class WellKnownControllerTest extends ControllerTestSupport {

    @Override
    protected Object controller() {
        return new WellKnownController("well-known/assetlinks-dev.json");
    }

    /**
     * 운영 환경(Spring Boot 기본 설정)에서는 Resource 반환값을 ResourceHttpMessageConverter가 그대로 쓴다.
     * 공통 support는 Jackson converter만 등록하므로 이 테스트에서만 Resource converter를 앞에 추가한다.
     */
    @BeforeEach
    void setUpResourceConverter() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(
                        new ResourceHttpMessageConverter(),
                        new MappingJackson2HttpMessageConverter(objectMapper)
                )
                .build();
    }

    @Test
    @DisplayName("Apple App Site Association 파일을 JSON으로 제공한다.")
    void appleAppSiteAssociation() throws Exception {
        mockMvc.perform(get("/.well-known/apple-app-site-association"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.applinks").exists());
    }

    @Test
    @DisplayName("설정된 Android assetlinks 파일을 JSON으로 제공한다.")
    void androidAssetLinks() throws Exception {
        mockMvc.perform(get("/.well-known/assetlinks.json"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].target.package_name").value("com.moneymong.moneymong.tb"));
    }
}
