package com.moneymong.domain.notification.push;

import java.util.List;

/**
 * 기기 토큰으로 알림을 보내는 수단(FCM 등).
 *
 * <p>알림 설정 확인, 이력 저장, 무효 토큰 정리를 빠뜨리지 않도록 {@code NotificationService}만 호출한다(ArchitectureTest로 검사).
 */
public interface PushSender {

    /**
     * 기기마다 메시지를 보낸다.
     *
     * <p>기기별 실패는 예외가 아니라 결과로 돌려준다. 결과는 {@code targets}와 같은 순서, 같은 개수다.
     */
    List<PushResult> send(List<PushTarget> targets, PushMessage message);
}
