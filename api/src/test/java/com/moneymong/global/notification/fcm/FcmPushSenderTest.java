package com.moneymong.global.notification.fcm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

import com.google.firebase.ErrorCode;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.SendResponse;
import com.moneymong.domain.notification.entity.enums.DevicePlatform;
import com.moneymong.domain.notification.entity.enums.NotificationType;
import com.moneymong.domain.notification.push.PushMessage;
import com.moneymong.domain.notification.push.PushResult;
import com.moneymong.domain.notification.push.PushTarget;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FcmPushSenderTest {

    private static final PushMessage MESSAGE = PushMessage.of(NotificationType.TEST, "제목", "본문");
    private static final FcmMessageFactory MESSAGE_FACTORY = new FcmMessageFactory("channel");

    @Mock
    private FirebaseMessaging androidMessaging;
    @Mock
    private FirebaseMessaging iosMessaging;

    private FcmPushSender fcmPushSender;

    @BeforeEach
    void setUp() {
        fcmPushSender = new FcmPushSender(
                Map.of(DevicePlatform.ANDROID, androidMessaging, DevicePlatform.IOS, iosMessaging),
                MESSAGE_FACTORY
        );
    }

    @Test
    @DisplayName("토큰마다 결과를 같은 순서로 돌려주고, 등록 해제된 토큰은 삭제 대상으로 표시한다.")
    void send() throws Exception {
        // given
        BatchResponse batchResponse = batchResponse(List.of(
                success("message-1"),
                failure(messagingException(MessagingErrorCode.UNREGISTERED)),
                failure(messagingException(MessagingErrorCode.SENDER_ID_MISMATCH)),
                failure(messagingException(MessagingErrorCode.UNAVAILABLE)),
                failure(messagingException(MessagingErrorCode.INVALID_ARGUMENT))
        ));
        given(androidMessaging.sendEach(anyList())).willReturn(batchResponse);

        // when
        List<PushResult> results = fcmPushSender.send(android("t1", "t2", "t3", "t4", "t5"), MESSAGE);

        // then
        assertThat(results).containsExactly(
                PushResult.success("message-1"),
                PushResult.failure("UNREGISTERED", true),
                PushResult.failure("SENDER_ID_MISMATCH", true),
                PushResult.failure("UNAVAILABLE", false),
                // payload 문제로도 나므로 토큰을 지우지 않는다.
                PushResult.failure("INVALID_ARGUMENT", false)
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("기기마다 플랫폼에 맞는 Firebase 프로젝트로 보내고, 결과는 넘긴 순서대로 돌려준다.")
    void sendByPlatform() throws Exception {
        // given
        BatchResponse androidResponse = batchResponse(List.of(success("a-1"), success("a-2")));
        BatchResponse iosResponse = batchResponse(List.of(success("i-1")));
        given(androidMessaging.sendEach(anyList())).willReturn(androidResponse);
        given(iosMessaging.sendEach(anyList())).willReturn(iosResponse);
        List<PushTarget> targets = List.of(
                new PushTarget("android-1", DevicePlatform.ANDROID),
                new PushTarget("ios-1", DevicePlatform.IOS),
                new PushTarget("android-2", DevicePlatform.ANDROID)
        );

        // when
        List<PushResult> results = fcmPushSender.send(targets, MESSAGE);

        // then
        assertThat(results).containsExactly(
                PushResult.success("a-1"),
                PushResult.success("i-1"),
                PushResult.success("a-2")
        );
        ArgumentCaptor<List<Message>> androidCaptor = ArgumentCaptor.forClass(List.class);
        then(androidMessaging).should().sendEach(androidCaptor.capture());
        assertThat(androidCaptor.getValue()).hasSize(2);
        ArgumentCaptor<List<Message>> iosCaptor = ArgumentCaptor.forClass(List.class);
        then(iosMessaging).should().sendEach(iosCaptor.capture());
        assertThat(iosCaptor.getValue()).hasSize(1);
    }

    @Test
    @DisplayName("인증 정보가 없는 플랫폼의 기기는 보내지 않고 NOT_CONFIGURED로 돌려준다. 토큰은 지우지 않는다.")
    void sendToNotConfiguredPlatform() throws Exception {
        // given
        FcmPushSender androidOnly = new FcmPushSender(Map.of(DevicePlatform.ANDROID, androidMessaging), MESSAGE_FACTORY);
        BatchResponse androidResponse = batchResponse(List.of(success("a-1")));
        given(androidMessaging.sendEach(anyList())).willReturn(androidResponse);
        List<PushTarget> targets = List.of(
                new PushTarget("ios-1", DevicePlatform.IOS),
                new PushTarget("android-1", DevicePlatform.ANDROID)
        );

        // when
        List<PushResult> results = androidOnly.send(targets, MESSAGE);

        // then
        assertThat(results).containsExactly(
                PushResult.failure(NoOpPushSender.NOT_CONFIGURED_ERROR_CODE, false),
                PushResult.success("a-1")
        );
        then(iosMessaging).shouldHaveNoInteractions();
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("토큰이 500개를 넘으면 500개씩 나눠 보낸다.")
    void sendInBatches() throws Exception {
        // given
        String[] tokens = IntStream.range(0, FcmPushSender.MAX_BATCH_SIZE + 1).mapToObj(i -> "token-" + i)
                .toArray(String[]::new);
        given(androidMessaging.sendEach(anyList())).willAnswer(invocation -> {
            List<Message> messages = invocation.getArgument(0);
            List<SendResponse> responses = new ArrayList<>();
            for (int i = 0; i < messages.size(); i++) {
                responses.add(success("message"));
            }
            return batchResponse(responses);
        });

        // when
        List<PushResult> results = fcmPushSender.send(android(tokens), MESSAGE);

        // then
        assertThat(results).hasSize(tokens.length);
        ArgumentCaptor<List<Message>> captor = ArgumentCaptor.forClass(List.class);
        then(androidMessaging).should(times(2)).sendEach(captor.capture());
        assertThat(captor.getAllValues()).extracting(List::size).containsExactly(FcmPushSender.MAX_BATCH_SIZE, 1);
    }

    @Test
    @DisplayName("묶음 전체가 실패하면 모든 토큰을 실패로 돌려주고 토큰은 지우지 않는다.")
    void wholeBatchFailure() throws Exception {
        // given
        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        given(exception.getErrorCode()).willReturn(ErrorCode.UNAUTHENTICATED);
        given(androidMessaging.sendEach(anyList())).willThrow(exception);

        // when
        List<PushResult> results = fcmPushSender.send(android("t1", "t2"), MESSAGE);

        // then
        assertThat(results).containsExactly(
                PushResult.failure("UNAUTHENTICATED", false),
                PushResult.failure("UNAUTHENTICATED", false)
        );
    }

    @Test
    @DisplayName("토큰이 없으면 FCM을 호출하지 않는다.")
    void sendWithoutTokens() {
        // when
        List<PushResult> results = fcmPushSender.send(List.of(), MESSAGE);

        // then
        assertThat(results).isEmpty();
        then(androidMessaging).shouldHaveNoInteractions();
        then(iosMessaging).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("오류 코드가 없으면 UNKNOWN으로 기록한다.")
    void unknownErrorCode() {
        // given
        FirebaseMessagingException withoutCode = mock(FirebaseMessagingException.class);

        // when, then
        assertThat(FcmPushSender.errorCodeOf(withoutCode)).isEqualTo(FcmPushSender.UNKNOWN_ERROR_CODE);
        assertThat(FcmPushSender.errorCodeOf(null)).isEqualTo(FcmPushSender.UNKNOWN_ERROR_CODE);
        assertThat(FcmPushSender.toResult(failure(null))).isEqualTo(PushResult.failure(FcmPushSender.UNKNOWN_ERROR_CODE, false));
    }

    private List<PushTarget> android(String... tokens) {
        return Arrays.stream(tokens)
                .map(token -> new PushTarget(token, DevicePlatform.ANDROID))
                .toList();
    }

    private BatchResponse batchResponse(List<SendResponse> responses) {
        BatchResponse batchResponse = mock(BatchResponse.class);
        given(batchResponse.getResponses()).willReturn(responses);
        return batchResponse;
    }

    private SendResponse success(String messageId) {
        SendResponse response = mock(SendResponse.class);
        given(response.isSuccessful()).willReturn(true);
        given(response.getMessageId()).willReturn(messageId);
        return response;
    }

    private SendResponse failure(FirebaseMessagingException exception) {
        SendResponse response = mock(SendResponse.class);
        given(response.isSuccessful()).willReturn(false);
        given(response.getException()).willReturn(exception);
        return response;
    }

    private FirebaseMessagingException messagingException(MessagingErrorCode errorCode) {
        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        given(exception.getMessagingErrorCode()).willReturn(errorCode);
        return exception;
    }
}
