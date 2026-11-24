package com.moneymong.domain.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneymong.domain.notification.entity.DeviceToken;
import com.moneymong.domain.notification.entity.NotificationHistory;
import com.moneymong.domain.notification.entity.enums.NotificationStatus;
import com.moneymong.domain.notification.push.PushMessage;
import com.moneymong.domain.notification.push.PushResult;
import com.moneymong.domain.notification.repository.DeviceTokenRepository;
import com.moneymong.domain.notification.repository.NotificationHistoryRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 발송 결과를 이력으로 남기고 무효 토큰을 지운다.
 *
 * <p>FCM 호출은 transaction 밖에서 하고 결과 저장만 transaction으로 묶기 위해 {@link NotificationService}와 분리했다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationRecorder {

    private final NotificationHistoryRepository notificationHistoryRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void recordNotDelivered(Long userId, PushMessage message, NotificationStatus status) {
        notificationHistoryRepository.save(
                NotificationHistory.notDelivered(userId, message.type(), toPayload(message), status)
        );

        log.info("[NOTIFICATION] 알림을 발송하지 않았다 userId={} type={} status={}", userId, message.type(), status);
    }

    @Transactional
    public void recordDelivered(
            Long userId,
            PushMessage message,
            List<DeviceDelivery> deliveries
    ) {
        String payload = toPayload(message);
        List<NotificationHistory> histories = new ArrayList<>();
        int successCount = 0;
        int deletedTokenCount = 0;

        for (DeviceDelivery delivery : deliveries) {
            DeviceToken deviceToken = delivery.deviceToken();
            PushResult result = delivery.result();

            histories.add(NotificationHistory.delivered(userId, message.type(), payload, deviceToken, result));

            if (result.success()) {
                successCount++;
            }
            if (result.invalidToken()) {
                deletedTokenCount += deviceTokenRepository.deleteByIdAndToken(deviceToken.getId(), deviceToken.getToken());
            }
        }

        notificationHistoryRepository.saveAll(histories);

        log.info("[NOTIFICATION] 알림을 발송했다 userId={} type={} deviceCount={} successCount={} deletedTokenCount={}",
                userId, message.type(), deliveries.size(), successCount, deletedTokenCount);
    }

    private String toPayload(PushMessage message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", message.title());
        payload.put("body", message.body());
        payload.put("data", message.appData());

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            // 문자열로만 이루어진 Map이라 직렬화에 실패하지 않는다.
            throw new IllegalStateException("알림 payload를 JSON으로 바꾸지 못했다.", e);
        }
    }
}
