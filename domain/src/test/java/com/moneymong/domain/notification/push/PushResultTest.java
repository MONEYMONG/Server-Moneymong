package com.moneymong.domain.notification.push;

import static org.assertj.core.api.Assertions.assertThat;

import com.moneymong.domain.notification.entity.enums.NotificationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PushResultTest {

    @Test
    @DisplayName("접수했으면 SUCCESS, 아니면 FAILED 상태다.")
    void status() {
        assertThat(PushResult.success("message-1").status()).isEqualTo(NotificationStatus.SUCCESS);
        assertThat(PushResult.failure("UNREGISTERED", true).status()).isEqualTo(NotificationStatus.FAILED);
        assertThat(PushResult.notConfigured().status()).isEqualTo(NotificationStatus.FAILED);
    }

    @Test
    @DisplayName("발송 수단이 설정되지 않은 결과는 NOT_CONFIGURED 실패이고 토큰은 지우지 않는다.")
    void notConfigured() {
        assertThat(PushResult.notConfigured())
                .isEqualTo(PushResult.failure(PushResult.NOT_CONFIGURED_ERROR_CODE, false));
        assertThat(PushResult.NOT_CONFIGURED_ERROR_CODE).isEqualTo("NOT_CONFIGURED");
    }
}
