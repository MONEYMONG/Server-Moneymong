package com.moneymong.domain.notification.service;

import com.moneymong.domain.notification.push.PushMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 업무 흐름에서 알림을 보낼 때 쓰는 비동기 진입점.
 *
 * <p>알림 전용 스레드 풀에서 {@link NotificationService#send}를 실행하므로 호출한 요청 스레드는 기다리지 않는다.
 * 업무 transaction이 커밋된 뒤에 보내려면 {@code @TransactionalEventListener(phase = AFTER_COMMIT)}에서 호출한다.
 * 스레드 풀은 메모리 안에 있어 서버가 재시작되면 대기 중인 알림은 사라진다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AsyncNotificationSender {

    public static final String EXECUTOR_NAME = "notificationExecutor";

    private final NotificationService notificationService;

    @Async(EXECUTOR_NAME)
    public void send(Long userId, PushMessage message) {
        try {
            notificationService.send(userId, message);
        } catch (Exception e) {
            // 비동기로 실행돼 호출한 쪽으로 예외가 전달되지 않는다. 알림 실패가 다른 작업에 영향을 주지 않도록 로그만 남긴다.
            log.error("[NOTIFICATION] 비동기 알림 발송에 실패했다 userId={} type={}", userId, message.type(), e);
        }
    }
}
