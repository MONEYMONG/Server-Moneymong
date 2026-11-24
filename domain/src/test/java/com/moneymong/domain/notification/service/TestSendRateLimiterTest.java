package com.moneymong.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class TestSendRateLimiterTest {

    private static final Long USER_ID = 1L;
    private static final String KEY = TestSendRateLimiter.KEY_PREFIX + USER_ID;

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private TestSendRateLimiter testSendRateLimiter;

    @BeforeEach
    void setUp() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
    }

    @Test
    @DisplayName("간격 안에 처음 보내면 허용하고 키를 간격만큼 남긴다.")
    void acquire() {
        // given
        given(valueOperations.setIfAbsent(KEY, "1", TestSendRateLimiter.INTERVAL)).willReturn(true);

        // when, then
        assertThat(testSendRateLimiter.tryAcquire(USER_ID)).isTrue();
    }

    @Test
    @DisplayName("간격 안에 다시 보내면 막는다.")
    void reject() {
        // given
        given(valueOperations.setIfAbsent(KEY, "1", TestSendRateLimiter.INTERVAL)).willReturn(false);

        // when, then
        assertThat(testSendRateLimiter.tryAcquire(USER_ID)).isFalse();
    }

    @Test
    @DisplayName("Redis가 결과를 주지 않으면 막는다.")
    void rejectWhenNoResult() {
        // given
        given(valueOperations.setIfAbsent(KEY, "1", TestSendRateLimiter.INTERVAL)).willReturn(null);

        // when, then
        assertThat(testSendRateLimiter.tryAcquire(USER_ID)).isFalse();
    }
}
