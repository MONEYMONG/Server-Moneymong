package com.moneymong.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.moneymong.domain.notification.entity.DeviceToken;
import com.moneymong.domain.notification.entity.enums.DevicePlatform;
import com.moneymong.domain.notification.entity.enums.NotificationStatus;
import com.moneymong.domain.notification.entity.enums.NotificationType;
import com.moneymong.domain.notification.push.PushMessage;
import com.moneymong.domain.notification.push.PushResult;
import com.moneymong.domain.notification.push.PushSender;
import com.moneymong.domain.notification.push.PushTarget;
import com.moneymong.domain.notification.repository.DeviceTokenRepository;
import com.moneymong.domain.notification.service.dto.request.SendTestNotificationRequest;
import com.moneymong.domain.notification.service.dto.response.NotificationDeliveryResponse;
import com.moneymong.domain.notification.service.dto.response.TestNotificationResponse;
import com.moneymong.domain.user.entity.User;
import com.moneymong.domain.user.repository.UserRepository;
import com.moneymong.global.exception.custom.BusinessException;
import com.moneymong.global.exception.custom.NotFoundException;
import com.moneymong.global.exception.enums.ErrorCode;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final Long USER_ID = 1L;
    private static final PushMessage MESSAGE = PushMessage.of(NotificationType.TEST, "제목", "본문");

    @Mock
    private UserRepository userRepository;
    @Mock
    private DeviceTokenRepository deviceTokenRepository;
    @Mock
    private PushSender pushSender;
    @Mock
    private NotificationRecorder notificationRecorder;
    @Mock
    private TestSendRateLimiter testSendRateLimiter;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    @DisplayName("알림을 켠 회원의 모든 기기로 플랫폼과 함께 보내고 기기별 결과를 기록한다.")
    void send() {
        // given
        DeviceToken android = deviceToken(10L, "device-1", "token-1", DevicePlatform.ANDROID);
        DeviceToken ios = deviceToken(11L, "device-2", "token-2", DevicePlatform.IOS);
        List<PushResult> results = List.of(PushResult.success("message-1"), PushResult.failure("UNREGISTERED", true));
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(true)));
        given(deviceTokenRepository.findAllByUserId(USER_ID)).willReturn(List.of(android, ios));
        given(pushSender.send(List.of(
                new PushTarget("token-1", DevicePlatform.ANDROID),
                new PushTarget("token-2", DevicePlatform.IOS)
        ), MESSAGE)).willReturn(results);

        // when
        List<NotificationDeliveryResponse> responses = notificationService.send(USER_ID, MESSAGE);

        // then
        assertThat(responses).extracting(
                NotificationDeliveryResponse::getDeviceId,
                NotificationDeliveryResponse::getPlatform,
                NotificationDeliveryResponse::getStatus,
                NotificationDeliveryResponse::getErrorCode
        ).containsExactly(
                org.assertj.core.groups.Tuple.tuple("device-1", DevicePlatform.ANDROID, NotificationStatus.SUCCESS, null),
                org.assertj.core.groups.Tuple.tuple("device-2", DevicePlatform.IOS, NotificationStatus.FAILED, "UNREGISTERED")
        );
        then(notificationRecorder).should().recordDelivered(USER_ID, MESSAGE, List.of(android, ios), results);
    }

    @Test
    @DisplayName("알림을 끈 회원에게는 보내지 않고 SKIPPED_DISABLED로 기록한다.")
    void skipWhenDisabled() {
        // given
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(false)));

        // when
        List<NotificationDeliveryResponse> responses = notificationService.send(USER_ID, MESSAGE);

        // then
        assertThat(responses).extracting(NotificationDeliveryResponse::getStatus)
                .containsExactly(NotificationStatus.SKIPPED_DISABLED);
        then(notificationRecorder).should().recordNotDelivered(USER_ID, MESSAGE, NotificationStatus.SKIPPED_DISABLED);
        then(deviceTokenRepository).shouldHaveNoInteractions();
        then(pushSender).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("등록된 기기가 없으면 보내지 않고 NO_DEVICE로 기록한다.")
    void noDevice() {
        // given
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(true)));
        given(deviceTokenRepository.findAllByUserId(USER_ID)).willReturn(List.of());

        // when
        List<NotificationDeliveryResponse> responses = notificationService.send(USER_ID, MESSAGE);

        // then
        assertThat(responses).extracting(NotificationDeliveryResponse::getStatus)
                .containsExactly(NotificationStatus.NO_DEVICE);
        then(notificationRecorder).should().recordNotDelivered(USER_ID, MESSAGE, NotificationStatus.NO_DEVICE);
        then(pushSender).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("없는 회원에게는 보내지 않는다.")
    void userNotFound() {
        // given
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

        // when, then
        assertThatThrownBy(() -> notificationService.send(USER_ID, MESSAGE))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_NOT_FOUND);
        then(notificationRecorder).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("발송 수단이 토큰 수와 다른 개수의 결과를 주면 기록하지 않고 실패시킨다.")
    void rejectMismatchedResults() {
        // given
        DeviceToken android = deviceToken(10L, "device-1", "token-1", DevicePlatform.ANDROID);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(true)));
        given(deviceTokenRepository.findAllByUserId(USER_ID)).willReturn(List.of(android));
        given(pushSender.send(anyList(), any())).willReturn(List.of());

        // when, then
        assertThatThrownBy(() -> notificationService.send(USER_ID, MESSAGE))
                .isInstanceOf(IllegalStateException.class);
        then(notificationRecorder).should(never()).recordDelivered(any(), any(), anyList(), anyList());
    }

    @Test
    @DisplayName("테스트 알림은 알림 설정과 관계없이 내 모든 기기로 보낸다.")
    void sendTestToAllDevices() {
        // given
        DeviceToken android = deviceToken(10L, "device-1", "token-1", DevicePlatform.ANDROID);
        given(testSendRateLimiter.tryAcquire(USER_ID)).willReturn(true);
        given(deviceTokenRepository.findAllByUserId(USER_ID)).willReturn(List.of(android));
        given(pushSender.send(List.of(new PushTarget("token-1", DevicePlatform.ANDROID)), NotificationService.TEST_MESSAGE))
                .willReturn(List.of(PushResult.success("message-1")));

        // when
        TestNotificationResponse response = notificationService.sendTest(USER_ID, SendTestNotificationRequest.allDevices());

        // then
        assertThat(response.getDeliveries()).extracting(NotificationDeliveryResponse::getStatus)
                .containsExactly(NotificationStatus.SUCCESS);
        then(userRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("테스트 알림에 deviceId를 주면 그 기기로만 보낸다.")
    void sendTestToOneDevice() {
        // given
        DeviceToken android = deviceToken(10L, "device-1", "token-1", DevicePlatform.ANDROID);
        DeviceToken ios = deviceToken(11L, "device-2", "token-2", DevicePlatform.IOS);
        given(testSendRateLimiter.tryAcquire(USER_ID)).willReturn(true);
        given(deviceTokenRepository.findAllByUserId(USER_ID)).willReturn(List.of(android, ios));
        given(pushSender.send(List.of(new PushTarget("token-2", DevicePlatform.IOS)), NotificationService.TEST_MESSAGE))
                .willReturn(List.of(PushResult.success("message-2")));

        // when
        TestNotificationResponse response = notificationService.sendTest(USER_ID, new SendTestNotificationRequest("device-2"));

        // then
        assertThat(response.getDeliveries()).extracting(NotificationDeliveryResponse::getDeviceId)
                .containsExactly("device-2");
    }

    @Test
    @DisplayName("테스트 알림의 deviceId가 내 기기가 아니면 404를 반환한다.")
    void sendTestToUnknownDevice() {
        // given
        DeviceToken android = deviceToken(10L, "device-1", "token-1", DevicePlatform.ANDROID);
        given(testSendRateLimiter.tryAcquire(USER_ID)).willReturn(true);
        given(deviceTokenRepository.findAllByUserId(USER_ID)).willReturn(List.of(android));

        // when, then
        assertThatThrownBy(() -> notificationService.sendTest(USER_ID, new SendTestNotificationRequest("other-device")))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOTIFICATION_DEVICE_NOT_FOUND);
        then(pushSender).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("기기가 없을 때 테스트 알림을 보내면 NO_DEVICE를 반환한다.")
    void sendTestWithoutDevice() {
        // given
        given(testSendRateLimiter.tryAcquire(USER_ID)).willReturn(true);
        given(deviceTokenRepository.findAllByUserId(USER_ID)).willReturn(List.of());

        // when
        TestNotificationResponse response = notificationService.sendTest(USER_ID, SendTestNotificationRequest.allDevices());

        // then
        assertThat(response.getDeliveries()).extracting(NotificationDeliveryResponse::getStatus)
                .containsExactly(NotificationStatus.NO_DEVICE);
    }

    @Test
    @DisplayName("테스트 알림을 너무 자주 보내면 429를 반환하고 보내지 않는다.")
    void sendTestTooOften() {
        // given
        given(testSendRateLimiter.tryAcquire(USER_ID)).willReturn(false);

        // when, then
        assertThatThrownBy(() -> notificationService.sendTest(USER_ID, SendTestNotificationRequest.allDevices()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOTIFICATION_TEST_TOO_MANY_REQUESTS);
        then(deviceTokenRepository).shouldHaveNoInteractions();
        then(pushSender).shouldHaveNoInteractions();
    }

    private User user(boolean pushEnabled) {
        User user = User.of("user@example.com", "머니몽", "KAKAO", "oauth-123");
        ReflectionTestUtils.setField(user, "id", USER_ID);
        user.updatePushEnabled(pushEnabled);
        return user;
    }

    private DeviceToken deviceToken(Long id, String deviceId, String token, DevicePlatform platform) {
        DeviceToken deviceToken = DeviceToken.of(USER_ID, deviceId, token, platform, ZonedDateTime.now());
        ReflectionTestUtils.setField(deviceToken, "id", id);
        return deviceToken;
    }
}
