package com.moneymong.domain.notification.entity;

import static lombok.AccessLevel.PRIVATE;
import static lombok.AccessLevel.PROTECTED;

import com.moneymong.domain.notification.entity.enums.DevicePlatform;
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
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.ZonedDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 알림을 받을 기기의 FCM 등록 토큰.
 *
 * <p>FCM 토큰은 로그인 세션이 아니라 앱 설치 단위로 발급되므로, 앱이 설치될 때 만든 {@code deviceId}를 기준으로 한 행을 둔다.
 * 같은 기기에서 다른 계정으로 로그인하면 {@code userId}가 바뀌고, 토큰이 갱신되면 {@code token}이 바뀐다.
 */
@Table(
        name = "device_tokens",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_device_tokens_device_id", columnNames = "device_id"),
                @UniqueConstraint(name = "uk_device_tokens_token", columnNames = "token")
        },
        indexes = @Index(name = "idx_device_tokens_user_id", columnList = "user_id")
)
@Entity
@Getter
@Builder(access = PRIVATE)
@AllArgsConstructor(access = PRIVATE)
@NoArgsConstructor(access = PROTECTED)
public class DeviceToken extends TimeBaseEntity {

    public static final int DEVICE_ID_MAX_LENGTH = 64;
    public static final int TOKEN_MAX_LENGTH = 512;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "device_id", nullable = false, length = DEVICE_ID_MAX_LENGTH)
    private String deviceId;

    @Column(nullable = false, length = TOKEN_MAX_LENGTH)
    private String token;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 16)
    private DevicePlatform platform;

    // 토큰을 마지막으로 등록한 시각. 오래 갱신되지 않은 토큰을 정리할 때 쓴다.
    @Column(name = "last_active_at", nullable = false)
    private ZonedDateTime lastActiveAt;

    public static DeviceToken of(
            Long userId,
            String deviceId,
            String token,
            DevicePlatform platform,
            ZonedDateTime registeredAt
    ) {
        return DeviceToken.builder()
                .userId(userId)
                .deviceId(deviceId)
                .token(token)
                .platform(platform)
                .lastActiveAt(registeredAt)
                .build();
    }

    public void refresh(Long userId, String token, DevicePlatform platform, ZonedDateTime registeredAt) {
        this.userId = userId;
        this.token = token;
        this.platform = platform;
        this.lastActiveAt = registeredAt;
    }
}
