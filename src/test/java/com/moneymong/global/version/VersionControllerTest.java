package com.moneymong.global.version;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moneymong.support.ControllerTestSupport;
import com.moneymong.global.exception.GlobalExceptionHandler;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.DisplayName;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;

class VersionControllerTest extends ControllerTestSupport {

    private static final String MINIMUM_VERSION = "1.2.0";

    @Override
    protected Object controller() {
        VersionController controller = new VersionController();
        ReflectionTestUtils.setField(controller, "minimumAppVersion", MINIMUM_VERSION);
        return controller;
    }

    /**
     * 운영 환경(Spring Boot 기본 설정)에서는 String 반환값을 StringHttpMessageConverter가 그대로 쓴다.
     * 공통 support는 Jackson converter만 등록하므로 이 테스트에서만 String converter를 앞에 추가한다.
     */
    @BeforeEach
    void setUpStringConverter() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(controller())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(
                        new StringHttpMessageConverter(StandardCharsets.UTF_8),
                        new MappingJackson2HttpMessageConverter(objectMapper)
                )
                .setValidator(validator)
                .build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.2.0", "1.2", "1.2.1", "1.10.0", "2.0.0"})
    @DisplayName("최소 버전 이상이면 최신 버전 안내 문구를 반환한다.")
    void upToDate(String version) throws Exception {
        mockMvc.perform(post("/api/v1/version")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("version", version))))
                .andExpect(status().isOk())
                .andExpect(content().string("Version is up to date."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.1.9", "1.1", "0.9.9", "1"})
    @DisplayName("최소 버전보다 낮으면 426과 업데이트 필요 코드를 반환한다.")
    void tooLow(String version) throws Exception {
        mockMvc.perform(post("/api/v1/version")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("version", version))))
                .andExpect(status().isUpgradeRequired())
                .andExpect(jsonPath("$.code").value("GLOBAL-426"));
    }
}
