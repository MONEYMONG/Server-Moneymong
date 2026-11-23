package com.moneymong.global.notification.fcm;

import com.moneymong.domain.notification.push.PushMessage;
import com.moneymong.domain.notification.push.PushResult;
import com.moneymong.domain.notification.push.PushSender;
import com.moneymong.domain.notification.push.PushTarget;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Android·iOS 모두 Firebase 인증 정보가 없을 때(로컬, 테스트) 쓰는 발송 수단. 실제로 보내지 않고 실패로 기록한다.
 */
@Slf4j
public class NoOpPushSender implements PushSender {

    @Override
    public List<PushResult> send(List<PushTarget> targets, PushMessage message) {
        log.info("[NOTIFICATION] Firebase가 설정되지 않아 알림을 보내지 않았다 type={} tokenCount={}",
                message.type(), targets.size());
        return Collections.nCopies(targets.size(), PushResult.notConfigured());
    }
}
