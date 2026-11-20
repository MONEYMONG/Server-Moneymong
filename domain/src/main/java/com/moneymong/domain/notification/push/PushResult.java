package com.moneymong.domain.notification.push;

/**
 * 기기 1대에 보낸 결과.
 *
 * @param success      발송 수단(FCM)이 메시지를 접수했는지
 * @param messageId    접수했을 때 발송 수단이 준 메시지 ID
 * @param errorCode    접수하지 않았을 때의 오류 코드
 * @param invalidToken 토큰이 더 이상 쓸 수 없어 삭제해야 하는지
 */
public record PushResult(
        boolean success,
        String messageId,
        String errorCode,
        boolean invalidToken
) {

    public static PushResult success(String messageId) {
        return new PushResult(true, messageId, null, false);
    }

    public static PushResult failure(String errorCode, boolean invalidToken) {
        return new PushResult(false, null, errorCode, invalidToken);
    }
}
