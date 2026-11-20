package com.moneymong.domain.notification.service.dto.request;

import com.moneymong.domain.notification.entity.DeviceToken;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SendTestNotificationRequest {

    // 비우면 내 모든 기기로 보낸다.
    @Size(max = DeviceToken.DEVICE_ID_MAX_LENGTH, message = "deviceId는 64자 이하로 입력해주세요.")
    private String deviceId;

    public static SendTestNotificationRequest allDevices() {
        return new SendTestNotificationRequest(null);
    }
}
