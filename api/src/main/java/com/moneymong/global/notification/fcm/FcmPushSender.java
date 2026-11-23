package com.moneymong.global.notification.fcm;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.SendResponse;
import com.moneymong.domain.notification.entity.enums.DevicePlatform;
import com.moneymong.domain.notification.push.PushMessage;
import com.moneymong.domain.notification.push.PushResult;
import com.moneymong.domain.notification.push.PushSender;
import com.moneymong.domain.notification.push.PushTarget;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;

/**
 * FCM으로 알림을 보낸다.
 *
 * <p>Android와 iOS는 서로 다른 Firebase 프로젝트를 쓰므로, 기기의 플랫폼에 맞는 프로젝트로 보낸다.
 * 다른 프로젝트로 보내면 FCM이 SENDER_ID_MISMATCH를 주고 토큰이 지워지기 때문이다.
 * 인증 정보가 없는 플랫폼의 기기는 보내지 않고 {@link NoOpPushSender#NOT_CONFIGURED_ERROR_CODE}로 돌려준다.
 */
@Slf4j
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

    private final Map<DevicePlatform, FirebaseMessaging> firebaseMessagings;
    private final FcmMessageFactory messageFactory;

    public FcmPushSender(Map<DevicePlatform, FirebaseMessaging> firebaseMessagings, FcmMessageFactory messageFactory) {
        this.firebaseMessagings = Map.copyOf(firebaseMessagings);
        this.messageFactory = messageFactory;
    }

    @Override
    public List<PushResult> send(List<PushTarget> targets, PushMessage message) {
        PushResult[] results = new PushResult[targets.size()];
        for (DevicePlatform platform : DevicePlatform.values()) {
            List<Integer> indexes = IntStream.range(0, targets.size())
                    .filter(i -> targets.get(i).platform() == platform)
                    .boxed()
                    .toList();
            if (indexes.isEmpty()) {
                continue;
            }

            List<String> tokens = indexes.stream()
                    .map(i -> targets.get(i).token())
                    .toList();
            List<PushResult> platformResults = sendToPlatform(platform, tokens, message);
            for (int i = 0; i < indexes.size(); i++) {
                results[indexes.get(i)] = platformResults.get(i);
            }
        }
        return List.of(results);
    }

    private List<PushResult> sendToPlatform(DevicePlatform platform, List<String> tokens, PushMessage message) {
        FirebaseMessaging firebaseMessaging = firebaseMessagings.get(platform);
        if (firebaseMessaging == null) {
            log.info("[NOTIFICATION] {} Firebase가 설정되지 않아 알림을 보내지 않았다 type={} tokenCount={}",
                    platform, message.type(), tokens.size());
            return Collections.nCopies(tokens.size(), PushResult.failure(NoOpPushSender.NOT_CONFIGURED_ERROR_CODE, false));
        }

        List<PushResult> results = new ArrayList<>(tokens.size());
        for (int from = 0; from < tokens.size(); from += MAX_BATCH_SIZE) {
            List<String> batch = tokens.subList(from, Math.min(from + MAX_BATCH_SIZE, tokens.size()));
            results.addAll(sendBatch(firebaseMessaging, platform, batch, message));
        }
        return results;
    }

    private List<PushResult> sendBatch(
            FirebaseMessaging firebaseMessaging,
            DevicePlatform platform,
            List<String> tokens,
            PushMessage message
    ) {
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
            log.warn("[NOTIFICATION] FCM 발송 요청이 실패했다 platform={} type={} tokenCount={} errorCode={}",
                    platform, message.type(), tokens.size(), errorCode);
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
