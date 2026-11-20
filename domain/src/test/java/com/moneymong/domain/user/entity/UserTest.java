package com.moneymong.domain.user.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserTest {

    @Test
    @DisplayName("새로 가입한 회원은 알림을 받는 상태로 시작한다.")
    void pushEnabledByDefault() {
        // when
        User fromFactory = User.of("user@example.com", "머니몽", "KAKAO", "oauth-123");
        User fromBuilder = User.builder().nickname("머니몽").provider("KAKAO").oauthId("oauth-123").build();

        // then
        assertThat(fromFactory.isPushEnabled()).isTrue();
        assertThat(fromBuilder.isPushEnabled()).isTrue();
    }

    @Test
    @DisplayName("알림 수신 여부를 바꾼다.")
    void updatePushEnabled() {
        // given
        User user = User.of("user@example.com", "머니몽", "KAKAO", "oauth-123");

        // when
        user.updatePushEnabled(false);

        // then
        assertThat(user.isPushEnabled()).isFalse();
    }
}
