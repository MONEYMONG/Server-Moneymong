package com.moneymong.domain.notification.entity;

import static lombok.AccessLevel.PRIVATE;
import static lombok.AccessLevel.PROTECTED;

import com.moneymong.domain.notification.entity.enums.DevicePlatform;
import com.moneymong.domain.notification.entity.enums.NotificationStatus;
import com.moneymong.domain.notification.entity.enums.NotificationType;
import com.moneymong.global.domain.TimeBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 알림 발송 이력. 기기 1대에 보낸 발송 1건이 한 행이다.
 *
 * <p>알림이 꺼져 있거나 등록된 기기가 없어 발송하지 않은 경우에는 기기 정보 없이 한 행을 남긴다.
 * {@code deviceTokenId}는 참고용이다. 토큰이 삭제되거나 다른 계정으로 바뀔 수 있으므로 수신자는 {@code userId}로 본다.
 * 토큰 원문은 그 자체로 발송 권한이라 저장하지 않는다.
 */
@Table(
        name = "notification_histories",
        indexes = @Index(name = "idx_notification_histories_user_id_created_at", columnList = "user_id, created_at")
)
@Entity
@Getter
@Builder(access = PRIVATE)
@AllArgsConstructor(access = PRIVATE)
@NoArgsConstructor(access = PROTECTED)
public class NotificationHistory extends TimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 50)
    private NotificationType type;

    // FCM에 보낸 title, body, data를 JSON으로 저장한다.
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "device_token_id")
    private Long deviceTokenId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 16)
    private DevicePlatform platform;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 32)
    private NotificationStatus status;

    @Column(name = "fcm_message_id")
    private String fcmMessageId;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    public static NotificationHistory delivered(
            Long userId,
            NotificationType type,
            String payload,
            DeviceToken deviceToken,
            boolean success,
            String fcmMessageId,
            String errorCode
    ) {
        return NotificationHistory.builder()
                .userId(userId)
                .type(type)
                .payload(payload)
                .deviceTokenId(deviceToken.getId())
                .platform(deviceToken.getPlatform())
                .status(success ? NotificationStatus.SUCCESS : NotificationStatus.FAILED)
                .fcmMessageId(fcmMessageId)
                .errorCode(errorCode)
                .build();
    }

    public static NotificationHistory notDelivered(
            Long userId,
            NotificationType type,
            String payload,
            NotificationStatus status
    ) {
        if (status == NotificationStatus.SUCCESS || status == NotificationStatus.FAILED) {
            throw new IllegalArgumentException("발송하지 않은 이력에는 SUCCESS, FAILED를 쓸 수 없다: " + status);
        }

        return NotificationHistory.builder()
                .userId(userId)
                .type(type)
                .payload(payload)
                .status(status)
                .build();
    }
}
