package com.moneymong.global.notification.fcm;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.SendResponse;
import com.moneymong.domain.notification.push.PushMessage;
import com.moneymong.domain.notification.push.PushResult;
import com.moneymong.domain.notification.push.PushSender;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * FCM으로 알림을 보낸다.
 */
@Slf4j
@RequiredArgsConstructor
public class FcmPushSender implements PushSender {

    // FCM sendEach 한 번에 보낼 수 있는 최대 메시지 수
    static final int MAX_BATCH_SIZE = 500;
    static final String UNKNOWN_ERROR_CODE = "UNKNOWN";

    /**
     * 토큰을 지워야 하는 오류. INVALID_ARGUMENT는 payload 문제로도 나므로 넣지 않는다
     * (payload를 잘못 만들면 모든 토큰이 지워질 수 있다).
     */
    private static final Set<MessagingErrorCode> INVALID_TOKEN_ERROR_CODES = EnumSet.of(
            MessagingErrorCode.UNREGISTERED,
            MessagingErrorCode.SENDER_ID_MISMATCH
    );

    private final FirebaseMessaging firebaseMessaging;
    private final FcmMessageFactory messageFactory;

    @Override
    public List<PushResult> send(List<String> tokens, PushMessage message) {
        List<PushResult> results = new ArrayList<>(tokens.size());
        for (int from = 0; from < tokens.size(); from += MAX_BATCH_SIZE) {
            List<String> batch = tokens.subList(from, Math.min(from + MAX_BATCH_SIZE, tokens.size()));
            results.addAll(sendBatch(batch, message));
        }
        return results;
    }

    private List<PushResult> sendBatch(List<String> tokens, PushMessage message) {
        List<Message> messages = tokens.stream()
                .map(token -> messageFactory.create(token, message))
                .toList();

        try {
            BatchResponse response = firebaseMessaging.sendEach(messages);
            return response.getResponses().stream()
                    .map(FcmPushSender::toResult)
                    .toList();
        } catch (FirebaseMessagingException e) {
            // 인증 실패처럼 묶음 전체가 실패한 경우. 기기별 결과로 바꿔 이력에 남긴다.
            String errorCode = errorCodeOf(e);
            log.warn("[NOTIFICATION] FCM 발송 요청이 실패했다 type={} tokenCount={} errorCode={}",
                    message.type(), tokens.size(), errorCode);
            return Collections.nCopies(tokens.size(), PushResult.failure(errorCode, false));
        }
    }

    static PushResult toResult(SendResponse response) {
        if (response.isSuccessful()) {
            return PushResult.success(response.getMessageId());
        }

        FirebaseMessagingException exception = response.getException();
        boolean invalidToken = exception != null && INVALID_TOKEN_ERROR_CODES.contains(exception.getMessagingErrorCode());
        return PushResult.failure(errorCodeOf(exception), invalidToken);
    }

    static String errorCodeOf(FirebaseMessagingException exception) {
        if (exception == null) {
            return UNKNOWN_ERROR_CODE;
        }
        if (exception.getMessagingErrorCode() != null) {
            return exception.getMessagingErrorCode().name();
        }
        if (exception.getErrorCode() != null) {
            return exception.getErrorCode().name();
        }
        return UNKNOWN_ERROR_CODE;
    }
}
