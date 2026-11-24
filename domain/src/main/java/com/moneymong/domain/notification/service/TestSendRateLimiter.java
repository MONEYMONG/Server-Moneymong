package com.moneymong.domain.notification.service;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 테스트 알림을 사용자마다 일정 간격에 한 번만 보내게 한다.
 */
@Component
@RequiredArgsConstructor
public class TestSendRateLimiter {

    static final Duration INTERVAL = Duration.ofSeconds(10);
    static final String KEY_PREFIX = "notification:test-send:";

    private final StringRedisTemplate redisTemplate;

    /**
     * 보낼 수 있으면 true를 돌려주고, 다음 간격이 지날 때까지 막는다.
     */
    public boolean tryAcquire(Long userId) {
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + userId, "1", INTERVAL);
        return Boolean.TRUE.equals(acquired);
    }
}
