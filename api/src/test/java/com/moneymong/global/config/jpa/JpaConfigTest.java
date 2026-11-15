package com.moneymong.global.config.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import com.moneymong.domain.agency.repository.AgencyRepository;
import com.moneymong.domain.auth.repository.RefreshTokenRepository;
import com.moneymong.domain.user.repository.UserRepository;
import java.util.Arrays;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * RefreshTokenRepository는 Redis repository라 JPA repository scan에서 제외해야 한다.
 * 제외 설정은 클래스 이름 regex라서 패키지를 옮기면 조용히 깨질 수 있다.
 * 이 프로젝트 테스트는 Spring context를 띄우지 않으므로 설정 값을 직접 검사한다.
 */
class JpaConfigTest {

    @Test
    @DisplayName("JPA repository scan은 RefreshTokenRepository만 제외한다.")
    void excludeOnlyRefreshTokenRepository() {
        EnableJpaRepositories annotation = JpaConfig.class.getAnnotation(EnableJpaRepositories.class);
        ComponentScan.Filter[] filters = annotation.excludeFilters();

        assertThat(filters).allSatisfy(filter -> assertThat(filter.type()).isEqualTo(FilterType.REGEX));
        Pattern[] patterns = Arrays.stream(filters)
                .flatMap(filter -> Arrays.stream(filter.pattern()))
                .map(Pattern::compile)
                .toArray(Pattern[]::new);

        assertThat(matchesAny(patterns, RefreshTokenRepository.class.getName())).isTrue();
        assertThat(matchesAny(patterns, UserRepository.class.getName())).isFalse();
        assertThat(matchesAny(patterns, AgencyRepository.class.getName())).isFalse();
    }

    // Spring의 RegexPatternTypeFilter는 클래스 이름 전체에 matches()를 적용한다.
    private boolean matchesAny(Pattern[] patterns, String className) {
        return Arrays.stream(patterns).anyMatch(pattern -> pattern.matcher(className).matches());
    }
}
