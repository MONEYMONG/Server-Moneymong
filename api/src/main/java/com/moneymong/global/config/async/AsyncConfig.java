package com.moneymong.global.config.async;

import com.moneymong.domain.notification.service.AsyncNotificationSender;
import java.util.concurrent.RejectedExecutionHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 비동기 실행 설정.
 *
 * <p>알림은 전용 스레드 풀에서 보내 다른 비동기 작업과 섞이지 않게 한다.
 * 대기열이 가득 차면 요청 스레드로 일을 되돌리지 않고(병목이 다시 생긴다) 버린 뒤 로그를 남긴다.
 * Executor bean을 직접 정의하면 Spring Boot의 기본 applicationTaskExecutor가 만들어지지 않는다.
 * 이름 없는 {@code @Async}가 생기면 그에 맞는 executor를 여기에 함께 둔다.
 */
@Slf4j
@EnableAsync
@Configuration
public class AsyncConfig {

    static final int NOTIFICATION_CORE_POOL_SIZE = 2;
    static final int NOTIFICATION_MAX_POOL_SIZE = 4;
    static final int NOTIFICATION_QUEUE_CAPACITY = 500;
    static final int NOTIFICATION_AWAIT_TERMINATION_SECONDS = 10;
    static final String NOTIFICATION_THREAD_NAME_PREFIX = "notification-";

    @Bean(name = AsyncNotificationSender.EXECUTOR_NAME)
    public ThreadPoolTaskExecutor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(NOTIFICATION_CORE_POOL_SIZE);
        executor.setMaxPoolSize(NOTIFICATION_MAX_POOL_SIZE);
        executor.setQueueCapacity(NOTIFICATION_QUEUE_CAPACITY);
        executor.setThreadNamePrefix(NOTIFICATION_THREAD_NAME_PREFIX);
        executor.setRejectedExecutionHandler(discardWithWarning());
        // 배포 중 종료될 때 대기 중인 알림을 가능한 한 보내고 끝낸다.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(NOTIFICATION_AWAIT_TERMINATION_SECONDS);
        return executor;
    }

    static RejectedExecutionHandler discardWithWarning() {
        return (task, executor) -> log.warn(
                "[NOTIFICATION] 알림 대기열이 가득 차 발송을 버렸다 queueSize={} activeCount={}",
                executor.getQueue().size(), executor.getActiveCount()
        );
    }
}
