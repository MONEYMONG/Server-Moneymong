package com.moneymong.domain.notification.service.dto.request;

import com.moneymong.domain.notification.entity.DeviceToken;
import jakarta.validation.constraints.Positive;
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

    // 넣으면 알림을 눌렀을 때 이 소속의 장부(LEDGER) 화면으로 이동한다. 비우면 앱만 연다.
    @Positive(message = "agencyId는 1 이상이어야 합니다.")
    private Long agencyId;

    public static SendTestNotificationRequest allDevices() {
        return new SendTestNotificationRequest(null, null);
    }
}
