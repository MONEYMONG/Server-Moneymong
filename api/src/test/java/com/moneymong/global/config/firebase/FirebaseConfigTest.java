package com.moneymong.global.config.firebase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.FirebaseApp;
import com.moneymong.domain.notification.entity.enums.DevicePlatform;
import com.moneymong.domain.notification.push.PushSender;
import com.moneymong.global.notification.fcm.FcmPushSender;
import com.moneymong.global.notification.fcm.NoOpPushSender;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FirebaseConfigTest {

    private static final String CHANNEL_ID = "moneymong_default";
    private static final String ANDROID_PROJECT_ID = "moneymong-test";
    private static final String IOS_PROJECT_ID = "ios-moneymong-test";

    private final FirebaseConfig firebaseConfig = new FirebaseConfig();

    @AfterEach
    void deleteFirebaseApps() {
        FirebaseApp.getApps().forEach(FirebaseApp::delete);
    }

    @Test
    @DisplayName("Android·iOS 인증 정보가 모두 없으면 실제로 보내지 않는 NoOpPushSender를 쓴다.")
    void noCredentials() {
        assertThat(firebaseConfig.pushSender("", "", CHANNEL_ID)).isInstanceOf(NoOpPushSender.class);
        assertThat(firebaseConfig.pushSender("  ", "  ", CHANNEL_ID)).isInstanceOf(NoOpPushSender.class);
        assertThat(FirebaseApp.getApps()).isEmpty();
    }

    @Test
    @DisplayName("플랫폼마다 서비스 계정 키로 Firebase 앱을 따로 만들고, 같은 앱을 다시 만들지 않는다.")
    void withCredentials() throws Exception {
        // given: 네트워크 없이 초기화만 확인하도록 임시 키로 서비스 계정 JSON을 만든다.
        String androidCredentials = encodedServiceAccount(ANDROID_PROJECT_ID);
        String iosCredentials = encodedServiceAccount(IOS_PROJECT_ID);

        // when
        PushSender pushSender = firebaseConfig.pushSender(androidCredentials, iosCredentials, CHANNEL_ID);
        FirebaseApp androidAgain = FirebaseConfig.initializeApp(DevicePlatform.ANDROID, androidCredentials);

        // then
        assertThat(pushSender).isInstanceOf(FcmPushSender.class);
        assertThat(FirebaseApp.getApps()).hasSize(2);
        assertThat(androidAgain.getName()).isEqualTo("moneymong-android");
        assertThat(androidAgain.getOptions().getProjectId()).isEqualTo(ANDROID_PROJECT_ID);
        assertThat(androidAgain.getOptions().getConnectTimeout()).isEqualTo(FirebaseConfig.CONNECT_TIMEOUT_MILLIS);
        assertThat(androidAgain.getOptions().getReadTimeout()).isEqualTo(FirebaseConfig.READ_TIMEOUT_MILLIS);
        assertThat(FirebaseApp.getInstance("moneymong-ios").getOptions().getProjectId()).isEqualTo(IOS_PROJECT_ID);
    }

    @Test
    @DisplayName("한 플랫폼의 인증 정보만 있으면 그 플랫폼만 FCM으로 보낸다.")
    void withOnePlatformCredentials() throws Exception {
        // when
        PushSender pushSender = firebaseConfig.pushSender(encodedServiceAccount(ANDROID_PROJECT_ID), "", CHANNEL_ID);

        // then
        assertThat(pushSender).isInstanceOf(FcmPushSender.class);
        assertThat(FirebaseApp.getApps()).extracting(FirebaseApp::getName).containsExactly("moneymong-android");
    }

    @Test
    @DisplayName("인증 정보를 서비스 계정 키로 읽지 못하면 어느 환경변수인지 알려 주고 시작하지 않는다.")
    void invalidCredentials() throws Exception {
        String notBase64 = "not-base64!!";
        String notServiceAccount = Base64.getEncoder().encodeToString("{}".getBytes(StandardCharsets.UTF_8));
        String valid = encodedServiceAccount(ANDROID_PROJECT_ID);

        assertThatThrownBy(() -> firebaseConfig.pushSender(notBase64, "", CHANNEL_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("FIREBASE_CREDENTIALS_ANDROID");
        assertThatThrownBy(() -> firebaseConfig.pushSender(valid, notServiceAccount, CHANNEL_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("FIREBASE_CREDENTIALS_IOS");
    }

    private String encodedServiceAccount(String projectId) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        String privateKey = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(generator.generateKeyPair().getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";

        Map<String, String> serviceAccount = new LinkedHashMap<>();
        serviceAccount.put("type", "service_account");
        serviceAccount.put("project_id", projectId);
        serviceAccount.put("private_key_id", "test-key-id");
        serviceAccount.put("private_key", privateKey);
        serviceAccount.put("client_email", "firebase-adminsdk@" + projectId + ".iam.gserviceaccount.com");
        serviceAccount.put("client_id", "1234567890");
        serviceAccount.put("token_uri", "https://oauth2.googleapis.com/token");

        byte[] json = new ObjectMapper().writeValueAsBytes(serviceAccount);
        return Base64.getEncoder().encodeToString(json);
    }
}
