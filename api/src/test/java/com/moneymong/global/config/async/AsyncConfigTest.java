package com.moneymong.global.config.async;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.moneymong.domain.notification.entity.enums.NotificationType;
import com.moneymong.domain.notification.push.PushMessage;
import com.moneymong.domain.notification.service.AsyncNotificationSender;
import com.moneymong.domain.notification.service.NotificationService;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class AsyncConfigTest {

    private static final Long USER_ID = 1L;
    private static final PushMessage MESSAGE = PushMessage.of(NotificationType.TEST, "제목", "본문");

    @Test
    @DisplayName("알림 전용 스레드 풀을 정해진 크기로 만든다.")
    void notificationExecutor() {
        // when
        ThreadPoolTaskExecutor executor = new AsyncConfig().notificationExecutor();

        // then
        assertThat(executor.getCorePoolSize()).isEqualTo(AsyncConfig.NOTIFICATION_CORE_POOL_SIZE);
        assertThat(executor.getMaxPoolSize()).isEqualTo(AsyncConfig.NOTIFICATION_MAX_POOL_SIZE);
        assertThat(executor.getQueueCapacity()).isEqualTo(AsyncConfig.NOTIFICATION_QUEUE_CAPACITY);
        assertThat(executor.getThreadNamePrefix()).isEqualTo(AsyncConfig.NOTIFICATION_THREAD_NAME_PREFIX);
    }

    @Test
    @DisplayName("대기열이 가득 차면 요청한 스레드에서 실행하지 않고 버린다.")
    void discardWhenQueueIsFull() {
        // given
        ThreadPoolExecutor executor = mock(ThreadPoolExecutor.class);
        given(executor.getQueue()).willReturn(new java.util.concurrent.LinkedBlockingQueue<>());
        Runnable task = () -> {
            throw new AssertionError("요청한 스레드에서 실행되면 안 된다.");
        };

        // when, then
        assertThatCode(() -> AsyncConfig.discardWithWarning().rejectedExecution(task, executor))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("AsyncNotificationSender는 호출한 스레드가 아니라 알림 전용 스레드에서 보낸다.")
    void sendOnNotificationThread() throws Exception {
        // given
        NotificationService notificationService = mock(NotificationService.class);
        CompletableFuture<String> sentThreadName = new CompletableFuture<>();
        given(notificationService.send(USER_ID, MESSAGE)).willAnswer(invocation -> {
            sentThreadName.complete(Thread.currentThread().getName());
            return List.of();
        });

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(NotificationService.class, () -> notificationService);
            context.register(AsyncConfig.class, AsyncNotificationSender.class);
            context.refresh();

            // when
            context.getBean(AsyncNotificationSender.class).send(USER_ID, MESSAGE);

            // then
            assertThat(sentThreadName.get(5, TimeUnit.SECONDS))
                    .startsWith(AsyncConfig.NOTIFICATION_THREAD_NAME_PREFIX)
                    .isNotEqualTo(Thread.currentThread().getName());
        }
    }
}
