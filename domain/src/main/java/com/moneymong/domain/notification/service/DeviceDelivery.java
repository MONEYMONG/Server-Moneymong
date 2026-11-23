package com.moneymong.domain.notification.service;

import com.moneymong.domain.notification.entity.DeviceToken;
import com.moneymong.domain.notification.push.PushResult;

/**
 * 기기 1대와 그 기기에 보낸 결과.
 *
 * <p>발송 결과는 토큰 순서대로 돌아오므로, 순서를 맞춰 짝짓는 일은 {@link NotificationService}에서 한 번만 하고 이 값을 넘긴다.
 */
public record DeviceDelivery(
        DeviceToken deviceToken,
        PushResult result
) {
}
