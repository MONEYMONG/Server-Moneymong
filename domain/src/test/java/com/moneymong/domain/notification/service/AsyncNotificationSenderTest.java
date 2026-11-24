package com.moneymong.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.moneymong.domain.notification.entity.enums.NotificationType;
import com.moneymong.domain.notification.push.PushMessage;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AsyncNotificationSenderTest {

    private static final Long USER_ID = 1L;
    private static final PushMessage MESSAGE = PushMessage.of(NotificationType.TEST, "제목", "본문");

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private AsyncNotificationSender asyncNotificationSender;

    @Test
    @DisplayName("NotificationService로 알림을 보낸다.")
    void send() {
        // given
        given(notificationService.send(USER_ID, MESSAGE)).willReturn(List.of());

        // when
        asyncNotificationSender.send(USER_ID, MESSAGE);

        // then
        then(notificationService).should().send(USER_ID, MESSAGE);
    }

    @Test
    @DisplayName("발송 중 예외가 나도 밖으로 던지지 않는다.")
    void swallowException() {
        // given
        given(notificationService.send(USER_ID, MESSAGE)).willThrow(new IllegalStateException("FCM 장애"));

        // when, then
        assertThatCode(() -> asyncNotificationSender.send(USER_ID, MESSAGE)).doesNotThrowAnyException();
    }
}
