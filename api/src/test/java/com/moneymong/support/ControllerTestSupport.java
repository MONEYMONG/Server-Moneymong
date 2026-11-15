package com.moneymong.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneymong.global.config.web.WebConfig;
import com.moneymong.global.exception.GlobalExceptionHandler;
import com.moneymong.domain.auth.dto.jwt.JwtAuthentication;
import com.moneymong.domain.auth.dto.jwt.JwtAuthenticationToken;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/**
 * Controller layer 테스트 공통 설정.
 *
 * <p>Spring context 없이 standalone MockMvc로 controller만 띄운다.
 * service는 하위 클래스에서 {@code @Mock}으로 주입하고, {@link #controller()}에서 controller 인스턴스를 반환한다.
 * 인증은 SecurityContext에 {@link JwtAuthentication} principal(id = {@link #USER_ID})을 넣어 처리한다.
 * security filter chain(permitAll 규칙, JWT 필터)은 이 테스트의 범위가 아니다.
 */
@ExtendWith(MockitoExtension.class)
public abstract class ControllerTestSupport {

    protected static final Long USER_ID = 1L;
    protected static final String ACCESS_TOKEN = "test-access-token";

    protected final ObjectMapper objectMapper = new WebConfig().objectMapper();

    protected MockMvc mockMvc;

    protected abstract Object controller();

    @BeforeEach
    void setUpMockMvc() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(controller())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(
                        new AuthenticationPrincipalArgumentResolver(),
                        new PageableHandlerMethodArgumentResolver()
                )
                // Spring Boot 기본 구성과 같은 순서: String, Resource 반환값은 Jackson보다 먼저 처리한다.
                .setMessageConverters(
                        new StringHttpMessageConverter(StandardCharsets.UTF_8),
                        new ResourceHttpMessageConverter(),
                        new MappingJackson2HttpMessageConverter(objectMapper)
                )
                .setValidator(validator)
                .build();

        JwtAuthentication principal = new JwtAuthentication(USER_ID, ACCESS_TOKEN);
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("USER")))
        );
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    protected String toJson(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
