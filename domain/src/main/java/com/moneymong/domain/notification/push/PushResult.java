package com.moneymong.domain.notification.push;

import com.moneymong.domain.notification.entity.enums.NotificationStatus;

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

    // 그 기기의 플랫폼에 Firebase 인증 정보가 없어 보내지 않았을 때의 오류 코드
    public static final String NOT_CONFIGURED_ERROR_CODE = "NOT_CONFIGURED";

    public static PushResult success(String messageId) {
        return new PushResult(true, messageId, null, false);
    }

    public static PushResult failure(String errorCode, boolean invalidToken) {
        return new PushResult(false, null, errorCode, invalidToken);
    }

    /**
     * 발송 수단이 설정되지 않아 보내지 않은 결과. 토큰 문제가 아니므로 토큰은 지우지 않는다.
     */
    public static PushResult notConfigured() {
        return failure(NOT_CONFIGURED_ERROR_CODE, false);
    }

    /**
     * 발송 이력과 응답에 쓰는 상태. 접수했으면 SUCCESS, 아니면 FAILED다.
     */
    public NotificationStatus status() {
        return success ? NotificationStatus.SUCCESS : NotificationStatus.FAILED;
    }
}
