package com.moneymong.global.config.firebase;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.moneymong.domain.notification.entity.enums.DevicePlatform;
import com.moneymong.domain.notification.push.PushSender;
import com.moneymong.global.notification.fcm.FcmMessageFactory;
import com.moneymong.global.notification.fcm.FcmPushSender;
import com.moneymong.global.notification.fcm.NoOpPushSender;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * 알림 발송 수단을 정한다.
 *
 * <p>Android와 iOS는 서로 다른 Firebase 프로젝트를 쓰므로 인증 정보를 따로 받는다
 * ({@code FIREBASE_CREDENTIALS_ANDROID}, {@code FIREBASE_CREDENTIALS_IOS}. 서비스 계정 JSON을 base64로 인코딩한 값).
 * 인증 정보가 있는 플랫폼은 FCM으로 보내고, 없는 플랫폼은 보내지 않는다.
 * 둘 다 없으면 {@link NoOpPushSender}를 쓴다. 로컬 실행과 테스트에 Firebase 키가 필요 없게 하기 위해서다.
 */
@Slf4j
@Configuration
public class FirebaseConfig {

    static final String APP_NAME_PREFIX = "moneymong-";
    static final String CREDENTIALS_ENV_PREFIX = "FIREBASE_CREDENTIALS_";
    static final int CONNECT_TIMEOUT_MILLIS = 3_000;
    static final int READ_TIMEOUT_MILLIS = 5_000;

    @Bean
    public PushSender pushSender(
            @Value("${notification.fcm.credentials.android:}") String androidCredentials,
            @Value("${notification.fcm.credentials.ios:}") String iosCredentials
    ) {
        Map<DevicePlatform, String> encodedCredentials = new EnumMap<>(DevicePlatform.class);
        encodedCredentials.put(DevicePlatform.ANDROID, androidCredentials);
        encodedCredentials.put(DevicePlatform.IOS, iosCredentials);

        Map<DevicePlatform, FirebaseMessaging> firebaseMessagings = new EnumMap<>(DevicePlatform.class);
        encodedCredentials.forEach((platform, credentials) -> {
            if (!StringUtils.hasText(credentials)) {
                log.warn("[NOTIFICATION] {}가 없어 {} 기기로는 알림을 실제로 보내지 않는다.", credentialsEnvName(platform), platform);
                return;
            }
            FirebaseApp firebaseApp = initializeApp(platform, credentials);
            log.info("[NOTIFICATION] {} 기기는 FCM으로 보낸다 projectId={}", platform, firebaseApp.getOptions().getProjectId());
            firebaseMessagings.put(platform, FirebaseMessaging.getInstance(firebaseApp));
        });

        if (firebaseMessagings.isEmpty()) {
            return new NoOpPushSender();
        }
        return new FcmPushSender(firebaseMessagings, new FcmMessageFactory());
    }

    static FirebaseApp initializeApp(DevicePlatform platform, String encodedCredentials) {
        GoogleCredentials credentials = readCredentials(platform, encodedCredentials);
        FirebaseOptions.Builder builder = FirebaseOptions.builder()
                .setCredentials(credentials)
                .setConnectTimeout(CONNECT_TIMEOUT_MILLIS)
                .setReadTimeout(READ_TIMEOUT_MILLIS);
        // 어느 Firebase 프로젝트로 보내는지 로그로 확인할 수 있게 서비스 계정의 project ID를 명시한다.
        if (credentials instanceof ServiceAccountCredentials serviceAccount && serviceAccount.getProjectId() != null) {
            builder.setProjectId(serviceAccount.getProjectId());
        }
        FirebaseOptions options = builder.build();

        String appName = appName(platform);
        return FirebaseApp.getApps().stream()
                .filter(app -> app.getName().equals(appName))
                .findFirst()
                .orElseGet(() -> FirebaseApp.initializeApp(options, appName));
    }

    static String appName(DevicePlatform platform) {
        return APP_NAME_PREFIX + platform.name().toLowerCase(Locale.ROOT);
    }

    static String credentialsEnvName(DevicePlatform platform) {
        return CREDENTIALS_ENV_PREFIX + platform.name();
    }

    private static GoogleCredentials readCredentials(DevicePlatform platform, String encodedCredentials) {
        try {
            byte[] json = Base64.getDecoder().decode(encodedCredentials.trim());
            return GoogleCredentials.fromStream(new ByteArrayInputStream(json));
        } catch (IllegalArgumentException | IOException e) {
            // 키 내용이 로그에 남지 않도록 원인 메시지를 붙이지 않는다.
            throw new IllegalStateException(
                    credentialsEnvName(platform) + "를 서비스 계정 키로 읽지 못했다. base64로 인코딩한 JSON인지 확인한다."
            );
        }
    }
}
