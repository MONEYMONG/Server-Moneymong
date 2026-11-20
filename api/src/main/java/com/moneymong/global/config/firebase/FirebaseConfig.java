package com.moneymong.global.config.firebase;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.moneymong.domain.notification.push.PushSender;
import com.moneymong.global.notification.fcm.FcmMessageFactory;
import com.moneymong.global.notification.fcm.FcmPushSender;
import com.moneymong.global.notification.fcm.NoOpPushSender;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * 알림 발송 수단을 정한다.
 *
 * <p>{@code FIREBASE_CREDENTIALS}(서비스 계정 JSON을 base64로 인코딩한 값)가 있으면 FCM으로 보내고,
 * 없으면 보내지 않는 {@link NoOpPushSender}를 쓴다. 로컬 실행과 테스트에 Firebase 키가 필요 없게 하기 위해서다.
 */
@Slf4j
@Configuration
public class FirebaseConfig {

    static final String APP_NAME = "moneymong";
    static final int CONNECT_TIMEOUT_MILLIS = 3_000;
    static final int READ_TIMEOUT_MILLIS = 5_000;

    @Bean
    public PushSender pushSender(
            @Value("${notification.fcm.credentials:}") String encodedCredentials,
            @Value("${notification.fcm.android-channel-id}") String androidChannelId
    ) {
        if (!StringUtils.hasText(encodedCredentials)) {
            log.warn("[NOTIFICATION] FIREBASE_CREDENTIALS가 없어 알림을 실제로 보내지 않는다.");
            return new NoOpPushSender();
        }

        FirebaseApp firebaseApp = initializeApp(encodedCredentials);
        log.info("[NOTIFICATION] FCM 발송을 사용한다 projectId={}", firebaseApp.getOptions().getProjectId());
        return new FcmPushSender(FirebaseMessaging.getInstance(firebaseApp), new FcmMessageFactory(androidChannelId));
    }

    static FirebaseApp initializeApp(String encodedCredentials) {
        GoogleCredentials credentials = readCredentials(encodedCredentials);
        FirebaseOptions.Builder builder = FirebaseOptions.builder()
                .setCredentials(credentials)
                .setConnectTimeout(CONNECT_TIMEOUT_MILLIS)
                .setReadTimeout(READ_TIMEOUT_MILLIS);
        // 어느 Firebase 프로젝트로 보내는지 로그로 확인할 수 있게 서비스 계정의 project ID를 명시한다.
        if (credentials instanceof ServiceAccountCredentials serviceAccount && serviceAccount.getProjectId() != null) {
            builder.setProjectId(serviceAccount.getProjectId());
        }
        FirebaseOptions options = builder.build();

        return FirebaseApp.getApps().stream()
                .filter(app -> app.getName().equals(APP_NAME))
                .findFirst()
                .orElseGet(() -> FirebaseApp.initializeApp(options, APP_NAME));
    }

    private static GoogleCredentials readCredentials(String encodedCredentials) {
        try {
            byte[] json = Base64.getDecoder().decode(encodedCredentials.trim());
            return GoogleCredentials.fromStream(new ByteArrayInputStream(json));
        } catch (IllegalArgumentException | IOException e) {
            // 키 내용이 로그에 남지 않도록 원인 메시지를 붙이지 않는다.
            throw new IllegalStateException("FIREBASE_CREDENTIALS를 서비스 계정 키로 읽지 못했다. base64로 인코딩한 JSON인지 확인한다.");
        }
    }
}
