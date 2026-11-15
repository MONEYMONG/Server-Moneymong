package com.moneymong.global.security.token.entity;

import com.moneymong.global.domain.TimeBaseEntity;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;
import org.springframework.data.redis.core.index.Indexed;

import static lombok.AccessLevel.PROTECTED;

@RedisHash(value = "refreshToken")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class RefreshToken extends TimeBaseEntity {


    @Id
    private Long userId;

    @Indexed
    private String token;

    private String role;

    // 만료 시각 (epoch millis). 만료 여부 판단에 사용한다.
    private long expiredAt;

    // Redis key TTL (seconds). 만료된 token이 Redis에 남지 않도록 한다.
    @TimeToLive
    private Long timeToLive;

    @Builder
    private RefreshToken(String token, Long userId, String role, long expiredAt, Long timeToLive) {
        this.token = token;
        this.userId = userId;
        this.role = role;
        this.expiredAt = expiredAt;
        this.timeToLive = timeToLive;
    }

    public void renew(String token, long expiredAt, long timeToLive) {
        this.token = token;
        this.expiredAt = expiredAt;
        this.timeToLive = timeToLive;
    }
}
