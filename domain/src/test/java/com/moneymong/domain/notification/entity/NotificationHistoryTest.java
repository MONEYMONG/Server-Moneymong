package com.moneymong.domain.notification.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moneymong.domain.notification.entity.enums.DevicePlatform;
import com.moneymong.domain.notification.entity.enums.NotificationStatus;
import com.moneymong.domain.notification.entity.enums.NotificationType;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

class NotificationHistoryTest {

    private static final Long USER_ID = 1L;
    private static final Long DEVICE_TOKEN_ID = 10L;
    private static final String PAYLOAD = "{\"title\":\"제목\"}";

    @Test
    @DisplayName("발송한 이력에는 기기 ID와 플랫폼을 남기고 토큰 원문은 남기지 않는다.")
    void delivered() {
        // given
        DeviceToken deviceToken = DeviceToken.of(USER_ID, "device-1", "fcm-token", DevicePlatform.IOS, ZonedDateTime.now());
        ReflectionTestUtils.setField(deviceToken, "id", DEVICE_TOKEN_ID);

        // when
        NotificationHistory success = NotificationHistory.delivered(USER_ID, NotificationType.TEST, PAYLOAD, deviceToken, true, "message-1", null);
        NotificationHistory failure = NotificationHistory.delivered(USER_ID, NotificationType.TEST, PAYLOAD, deviceToken, false, null, "UNREGISTERED");

        // then
        assertThat(success.getStatus()).isEqualTo(NotificationStatus.SUCCESS);
        assertThat(success.getFcmMessageId()).isEqualTo("message-1");
        assertThat(success.getDeviceTokenId()).isEqualTo(DEVICE_TOKEN_ID);
        assertThat(success.getPlatform()).isEqualTo(DevicePlatform.IOS);
        assertThat(success.getUserId()).isEqualTo(USER_ID);
        assertThat(success.getPayload()).isEqualTo(PAYLOAD);
        assertThat(failure.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(failure.getErrorCode()).isEqualTo("UNREGISTERED");
    }

    @ParameterizedTest
    @EnumSource(value = NotificationStatus.class, names = {"SKIPPED_DISABLED", "NO_DEVICE"})
    @DisplayName("발송하지 않은 이력은 기기 정보 없이 상태만 남긴다.")
    void notDelivered(NotificationStatus status) {
        // when
        NotificationHistory history = NotificationHistory.notDelivered(USER_ID, NotificationType.TEST, PAYLOAD, status);

        // then
        assertThat(history.getStatus()).isEqualTo(status);
        assertThat(history.getDeviceTokenId()).isNull();
        assertThat(history.getPlatform()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = NotificationStatus.class, names = {"SUCCESS", "FAILED"})
    @DisplayName("발송하지 않은 이력에 발송 결과 상태를 쓸 수 없다.")
    void rejectDeliveryStatus(NotificationStatus status) {
        assertThatThrownBy(() -> NotificationHistory.notDelivered(USER_ID, NotificationType.TEST, PAYLOAD, status))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
