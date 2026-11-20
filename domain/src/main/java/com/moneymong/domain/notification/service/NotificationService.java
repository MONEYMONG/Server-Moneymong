package com.moneymong.domain.notification.service;

import com.moneymong.domain.notification.entity.DeviceToken;
import com.moneymong.domain.notification.entity.enums.NotificationStatus;
import com.moneymong.domain.notification.entity.enums.NotificationType;
import com.moneymong.domain.notification.push.PushMessage;
import com.moneymong.domain.notification.push.PushResult;
import com.moneymong.domain.notification.push.PushSender;
import com.moneymong.domain.notification.repository.DeviceTokenRepository;
import com.moneymong.domain.notification.service.dto.request.SendTestNotificationRequest;
import com.moneymong.domain.notification.service.dto.response.NotificationDeliveryResponse;
import com.moneymong.domain.notification.service.dto.response.TestNotificationResponse;
import com.moneymong.domain.user.entity.User;
import com.moneymong.domain.user.repository.UserRepository;
import com.moneymong.global.exception.custom.BusinessException;
import com.moneymong.global.exception.custom.NotFoundException;
import com.moneymong.global.exception.enums.ErrorCode;
import java.util.List;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 알림 발송 진입점. 알림은 이 서비스로만 보낸다.
 *
 * <p>동기로 실행된다. 업무 흐름에서 보낼 때는 {@link AsyncNotificationSender}로 감싸 요청 스레드를 붙잡지 않는다.
 * FCM 호출 중에 DB connection을 쥐고 있지 않도록 class 전체를 transaction으로 묶지 않는다.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    static final PushMessage TEST_MESSAGE = PushMessage.of(
            NotificationType.TEST,
            "머니몽 테스트 알림",
            "알림이 정상적으로 도착했어요."
    );

    private final UserRepository userRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final PushSender pushSender;
    private final NotificationRecorder notificationRecorder;
    private final TestSendRateLimiter testSendRateLimiter;

    /**
     * 사용자의 모든 기기로 알림을 보낸다. 사용자가 알림을 껐으면 보내지 않고 이력만 남긴다.
     */
    public List<NotificationDeliveryResponse> send(Long userId, PushMessage message) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND));

        if (!user.isPushEnabled()) {
            notificationRecorder.recordNotDelivered(userId, message, NotificationStatus.SKIPPED_DISABLED);
            return List.of(NotificationDeliveryResponse.notDelivered(NotificationStatus.SKIPPED_DISABLED));
        }

        return dispatch(userId, message, deviceTokenRepository.findAllByUserId(userId));
    }

    /**
     * 내 기기로 테스트 알림을 보낸다. 직접 요청한 발송이라 알림 설정은 보지 않는다.
     */
    public TestNotificationResponse sendTest(Long userId, SendTestNotificationRequest request) {
        if (!testSendRateLimiter.tryAcquire(userId)) {
            throw new BusinessException(ErrorCode.NOTIFICATION_TEST_TOO_MANY_REQUESTS);
        }

        List<DeviceToken> deviceTokens = findTestTargets(userId, request.getDeviceId());
        return new TestNotificationResponse(dispatch(userId, TEST_MESSAGE, deviceTokens));
    }

    private List<DeviceToken> findTestTargets(Long userId, String deviceId) {
        List<DeviceToken> deviceTokens = deviceTokenRepository.findAllByUserId(userId);
        if (deviceId == null) {
            return deviceTokens;
        }

        List<DeviceToken> targets = deviceTokens.stream()
                .filter(deviceToken -> deviceToken.getDeviceId().equals(deviceId))
                .toList();
        if (targets.isEmpty()) {
            throw new NotFoundException(ErrorCode.NOTIFICATION_DEVICE_NOT_FOUND);
        }
        return targets;
    }

    private List<NotificationDeliveryResponse> dispatch(Long userId, PushMessage message, List<DeviceToken> deviceTokens) {
        if (deviceTokens.isEmpty()) {
            notificationRecorder.recordNotDelivered(userId, message, NotificationStatus.NO_DEVICE);
            return List.of(NotificationDeliveryResponse.notDelivered(NotificationStatus.NO_DEVICE));
        }

        List<String> tokens = deviceTokens.stream()
                .map(DeviceToken::getToken)
                .toList();
        List<PushResult> results = pushSender.send(tokens, message);
        if (results.size() != tokens.size()) {
            throw new IllegalStateException(
                    "발송 결과 수가 토큰 수와 다르다. tokens=" + tokens.size() + " results=" + results.size()
            );
        }

        notificationRecorder.recordDelivered(userId, message, deviceTokens, results);

        return IntStream.range(0, deviceTokens.size())
                .mapToObj(i -> NotificationDeliveryResponse.delivered(deviceTokens.get(i), results.get(i)))
                .toList();
    }
}
