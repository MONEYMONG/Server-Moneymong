package com.moneymong.domain.notification.service;

import com.moneymong.domain.user.event.UserWithdrawnEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 회원이 탈퇴하면 그 회원의 디바이스 토큰을 지워 더 이상 알림이 가지 않게 한다.
 *
 * <p>탈퇴와 같은 transaction에서 실행돼야 하므로 {@code @EventListener}를 사용한다. 발송 이력은 남긴다.
 */
@Component
@RequiredArgsConstructor
public class UserWithdrawnEventListener {

    private final DeviceTokenService deviceTokenService;

    @EventListener
    public void deleteDeviceTokens(UserWithdrawnEvent event) {
        deviceTokenService.deleteAll(event.userId());
    }
}
