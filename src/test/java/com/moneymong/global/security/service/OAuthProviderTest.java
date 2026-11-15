package com.moneymong.global.security.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moneymong.global.exception.custom.NotFoundException;
import com.moneymong.global.exception.enums.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class OAuthProviderTest {

    @Test
    @DisplayName("이름으로 로그인 수단을 찾는다.")
    void get() {
        assertThat(OAuthProvider.get("KAKAO")).isEqualTo(OAuthProvider.KAKAO);
        assertThat(OAuthProvider.get("APPLE")).isEqualTo(OAuthProvider.APPLE);
        assertThat(OAuthProvider.KAKAO.getName()).isEqualTo("KAKAO");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"GOOGLE", "kakao"})
    @DisplayName("지원하지 않는 이름이면 INVALID_PROVIDER 예외가 발생한다.")
    void getInvalid(String name) {
        assertThatThrownBy(() -> OAuthProvider.get(name))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_PROVIDER);
    }
}
