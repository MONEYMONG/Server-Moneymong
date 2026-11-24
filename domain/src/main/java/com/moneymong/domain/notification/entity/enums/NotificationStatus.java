package com.moneymong.domain.notification.entity.enums;

/**
 * 알림 발송 이력의 결과.
 *
 * <p>SUCCESS와 FAILED는 FCM이 메시지를 접수했는지를 뜻한다. 기기에 도착했는지는 알 수 없다.
 */
public enum NotificationStatus {
    /** FCM이 메시지를 접수했다. */
    SUCCESS,
    /** FCM이 메시지를 거절했거나 호출에 실패했다. */
    FAILED,
    /** 사용자가 알림을 꺼 두어 발송하지 않았다. */
    SKIPPED_DISABLED,
    /** 등록된 기기가 없어 발송하지 않았다. */
    NO_DEVICE
}
