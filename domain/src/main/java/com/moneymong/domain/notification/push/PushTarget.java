package com.moneymong.domain.notification.push;

import com.moneymong.domain.notification.entity.enums.DevicePlatform;
import java.util.Objects;

/**
 * 알림을 받을 기기 1대.
 *
 * <p>Android와 iOS가 서로 다른 Firebase 프로젝트를 쓰므로, 발송 수단이 어느 프로젝트로 보낼지 고르도록 플랫폼을 함께 넘긴다.
 *
 * @param token    FCM 등록 토큰
 * @param platform 토큰을 발급한 기기의 플랫폼
 */
public record PushTarget(
        String token,
        DevicePlatform platform
) {

    public PushTarget {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(platform, "platform");
    }
}
