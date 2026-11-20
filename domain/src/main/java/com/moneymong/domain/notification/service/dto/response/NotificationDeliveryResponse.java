package com.moneymong.domain.notification.service.dto.response;

import com.moneymong.domain.notification.entity.DeviceToken;
import com.moneymong.domain.notification.entity.enums.DevicePlatform;
import com.moneymong.domain.notification.entity.enums.NotificationStatus;
import com.moneymong.domain.notification.push.PushResult;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 기기 1대에 보낸 결과. 발송하지 않았으면(알림 꺼짐, 기기 없음) 기기 정보 없이 상태만 담는다.
 */
@Getter
@AllArgsConstructor
public class NotificationDeliveryResponse {

    private String deviceId;
    private DevicePlatform platform;
    private NotificationStatus status;
    private String errorCode;

    public static NotificationDeliveryResponse delivered(DeviceToken deviceToken, PushResult result) {
        return new NotificationDeliveryResponse(
                deviceToken.getDeviceId(),
                deviceToken.getPlatform(),
                result.success() ? NotificationStatus.SUCCESS : NotificationStatus.FAILED,
                result.errorCode()
        );
    }

    public static NotificationDeliveryResponse notDelivered(NotificationStatus status) {
        return new NotificationDeliveryResponse(null, null, status, null);
    }
}
