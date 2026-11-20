package com.moneymong.domain.notification.api;

import com.moneymong.domain.auth.dto.jwt.JwtAuthentication;
import com.moneymong.domain.notification.service.DeviceTokenService;
import com.moneymong.domain.notification.service.NotificationService;
import com.moneymong.domain.notification.service.dto.request.RegisterDeviceTokenRequest;
import com.moneymong.domain.notification.service.dto.request.SendTestNotificationRequest;
import com.moneymong.domain.notification.service.dto.response.TestNotificationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "10. [알림]")
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@RestController
public class NotificationController {

    private final DeviceTokenService deviceTokenService;
    private final NotificationService notificationService;

    @Operation(
            summary = "디바이스 토큰 등록·갱신",
            description = "로그인 직후, 앱 실행 시, FCM 토큰이 갱신될 때 호출한다. deviceId는 앱 설치 때 만들어 저장해 둔 값이다."
    )
    @PutMapping("/devices")
    public void registerDevice(
            @AuthenticationPrincipal JwtAuthentication user,
            @RequestBody @Valid RegisterDeviceTokenRequest request
    ) {
        deviceTokenService.register(user.getId(), request);
    }

    @Operation(summary = "디바이스 토큰 삭제", description = "로그아웃하기 전에 호출한다.")
    @DeleteMapping("/devices/{deviceId}")
    public void unregisterDevice(
            @AuthenticationPrincipal JwtAuthentication user,
            @PathVariable String deviceId
    ) {
        deviceTokenService.unregister(user.getId(), deviceId);
    }

    @Operation(
            summary = "내 기기로 테스트 알림 발송",
            description = "deviceId를 비우면 내 모든 기기로 보낸다. 알림 설정과 관계없이 보내며, 10초에 한 번 보낼 수 있다."
    )
    @PostMapping("/test")
    public TestNotificationResponse sendTestNotification(
            @AuthenticationPrincipal JwtAuthentication user,
            @RequestBody(required = false) @Valid SendTestNotificationRequest request
    ) {
        SendTestNotificationRequest target = request == null ? SendTestNotificationRequest.allDevices() : request;
        return notificationService.sendTest(user.getId(), target);
    }
}
