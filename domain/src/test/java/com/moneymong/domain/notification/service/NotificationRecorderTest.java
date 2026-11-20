package com.moneymong.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneymong.domain.notification.entity.DeviceToken;
import com.moneymong.domain.notification.entity.NotificationHistory;
import com.moneymong.domain.notification.entity.enums.DevicePlatform;
import com.moneymong.domain.notification.entity.enums.NotificationStatus;
import com.moneymong.domain.notification.entity.enums.NotificationType;
import com.moneymong.domain.notification.push.PushMessage;
import com.moneymong.domain.notification.push.PushResult;
import com.moneymong.domain.notification.repository.DeviceTokenRepository;
import com.moneymong.domain.notification.repository.NotificationHistoryRepository;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NotificationRecorderTest {

    private static final Long USER_ID = 1L;
    private static final PushMessage MESSAGE = new PushMessage(
            NotificationType.TEST, "제목", "본문", Map.of("link", "moneymong://home")
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private NotificationHistoryRepository notificationHistoryRepository;
    @Mock
    private DeviceTokenRepository deviceTokenRepository;

    private NotificationRecorder notificationRecorder;

    @BeforeEach
    void setUp() {
        notificationRecorder = new NotificationRecorder(notificationHistoryRepository, deviceTokenRepository, objectMapper);
    }

    @Test
    @DisplayName("발송하지 않은 알림은 상태와 payload만 이력으로 남긴다.")
    void recordNotDelivered() throws Exception {
        // when
        notificationRecorder.recordNotDelivered(USER_ID, MESSAGE, NotificationStatus.NO_DEVICE);

        // then
        ArgumentCaptor<NotificationHistory> captor = ArgumentCaptor.forClass(NotificationHistory.class);
        then(notificationHistoryRepository).should().save(captor.capture());
        NotificationHistory history = captor.getValue();
        assertThat(history.getUserId()).isEqualTo(USER_ID);
        assertThat(history.getType()).isEqualTo(NotificationType.TEST);
        assertThat(history.getStatus()).isEqualTo(NotificationStatus.NO_DEVICE);
        assertPayload(history.getPayload());
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("기기마다 결과를 이력으로 남기고, 무효 토큰은 발송에 쓴 토큰일 때만 지운다.")
    void recordDelivered() throws Exception {
        // given
        DeviceToken android = deviceToken(10L, "device-1", "token-1", DevicePlatform.ANDROID);
        DeviceToken ios = deviceToken(11L, "device-2", "token-2", DevicePlatform.IOS);
        given(deviceTokenRepository.deleteByIdAndToken(11L, "token-2")).willReturn(1);

        // when
        notificationRecorder.recordDelivered(
                USER_ID,
                MESSAGE,
                List.of(android, ios),
                List.of(PushResult.success("message-1"), PushResult.failure("UNREGISTERED", true))
        );

        // then
        ArgumentCaptor<List<NotificationHistory>> captor = ArgumentCaptor.forClass(List.class);
        then(notificationHistoryRepository).should().saveAll(captor.capture());
        List<NotificationHistory> histories = captor.getValue();
        assertThat(histories).extracting(
                NotificationHistory::getDeviceTokenId,
                NotificationHistory::getPlatform,
                NotificationHistory::getStatus,
                NotificationHistory::getFcmMessageId,
                NotificationHistory::getErrorCode
        ).containsExactly(
                org.assertj.core.groups.Tuple.tuple(10L, DevicePlatform.ANDROID, NotificationStatus.SUCCESS, "message-1", null),
                org.assertj.core.groups.Tuple.tuple(11L, DevicePlatform.IOS, NotificationStatus.FAILED, null, "UNREGISTERED")
        );
        assertPayload(histories.get(0).getPayload());
        then(deviceTokenRepository).should().deleteByIdAndToken(11L, "token-2");
    }

    @Test
    @DisplayName("일시적인 실패는 토큰을 지우지 않는다.")
    void keepTokenOnTemporaryFailure() {
        // given
        DeviceToken android = deviceToken(10L, "device-1", "token-1", DevicePlatform.ANDROID);

        // when
        notificationRecorder.recordDelivered(
                USER_ID, MESSAGE, List.of(android), List.of(PushResult.failure("UNAVAILABLE", false))
        );

        // then
        then(deviceTokenRepository).should(never()).deleteByIdAndToken(anyLong(), anyString());
    }

    private void assertPayload(String payload) throws Exception {
        JsonNode json = objectMapper.readTree(payload);
        assertThat(json.get("title").asText()).isEqualTo("제목");
        assertThat(json.get("body").asText()).isEqualTo("본문");
        assertThat(json.get("data").get("link").asText()).isEqualTo("moneymong://home");
        assertThat(json.get("data").get("type").asText()).isEqualTo("TEST");
    }

    private DeviceToken deviceToken(Long id, String deviceId, String token, DevicePlatform platform) {
        DeviceToken deviceToken = DeviceToken.of(USER_ID, deviceId, token, platform, ZonedDateTime.now());
        ReflectionTestUtils.setField(deviceToken, "id", id);
        return deviceToken;
    }
}
