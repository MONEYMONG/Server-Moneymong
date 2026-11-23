package com.moneymong.global.notification.fcm;

import static org.assertj.core.api.Assertions.assertThat;

import com.moneymong.domain.notification.entity.enums.DevicePlatform;
import com.moneymong.domain.notification.entity.enums.NotificationType;
import com.moneymong.domain.notification.push.PushMessage;
import com.moneymong.domain.notification.push.PushResult;
import com.moneymong.domain.notification.push.PushTarget;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NoOpPushSenderTest {

    @Test
    @DisplayName("보내지 않고 모든 토큰을 NOT_CONFIGURED 실패로 돌려준다. 토큰은 지우지 않는다.")
    void send() {
        // when
        List<PushResult> results = new NoOpPushSender().send(
                List.of(new PushTarget("t1", DevicePlatform.ANDROID), new PushTarget("t2", DevicePlatform.IOS)),
                PushMessage.of(NotificationType.TEST, "제목", "본문")
        );

        // then
        assertThat(results).containsExactly(
                PushResult.failure(NoOpPushSender.NOT_CONFIGURED_ERROR_CODE, false),
                PushResult.failure(NoOpPushSender.NOT_CONFIGURED_ERROR_CODE, false)
        );
    }
}
