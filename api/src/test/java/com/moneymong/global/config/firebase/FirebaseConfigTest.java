package com.moneymong.global.config.firebase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.FirebaseApp;
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
    private static final String PROJECT_ID = "moneymong-test";

    private final FirebaseConfig firebaseConfig = new FirebaseConfig();

    @AfterEach
    void deleteFirebaseApps() {
        FirebaseApp.getApps().forEach(FirebaseApp::delete);
    }

    @Test
    @DisplayName("인증 정보가 없으면 실제로 보내지 않는 NoOpPushSender를 쓴다.")
    void noCredentials() {
        assertThat(firebaseConfig.pushSender("", CHANNEL_ID)).isInstanceOf(NoOpPushSender.class);
        assertThat(firebaseConfig.pushSender("  ", CHANNEL_ID)).isInstanceOf(NoOpPushSender.class);
    }

    @Test
    @DisplayName("서비스 계정 키가 있으면 FCM으로 보내고, 같은 앱을 다시 만들지 않는다.")
    void withCredentials() throws Exception {
        // given: 네트워크 없이 초기화만 확인하도록 임시 키로 서비스 계정 JSON을 만든다.
        String credentials = encodedServiceAccount();

        // when
        PushSender pushSender = firebaseConfig.pushSender(credentials, CHANNEL_ID);
        FirebaseApp again = FirebaseConfig.initializeApp(credentials);

        // then
        assertThat(pushSender).isInstanceOf(FcmPushSender.class);
        assertThat(again.getName()).isEqualTo(FirebaseConfig.APP_NAME);
        assertThat(again.getOptions().getProjectId()).isEqualTo(PROJECT_ID);
        assertThat(again.getOptions().getConnectTimeout()).isEqualTo(FirebaseConfig.CONNECT_TIMEOUT_MILLIS);
        assertThat(again.getOptions().getReadTimeout()).isEqualTo(FirebaseConfig.READ_TIMEOUT_MILLIS);
        assertThat(FirebaseApp.getApps()).hasSize(1);
    }

    @Test
    @DisplayName("인증 정보를 서비스 계정 키로 읽지 못하면 시작하지 않는다.")
    void invalidCredentials() {
        String notBase64 = "not-base64!!";
        String notServiceAccount = Base64.getEncoder().encodeToString("{}".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> firebaseConfig.pushSender(notBase64, CHANNEL_ID))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> firebaseConfig.pushSender(notServiceAccount, CHANNEL_ID))
                .isInstanceOf(IllegalStateException.class);
    }

    private String encodedServiceAccount() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        String privateKey = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(generator.generateKeyPair().getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";

        Map<String, String> serviceAccount = new LinkedHashMap<>();
        serviceAccount.put("type", "service_account");
        serviceAccount.put("project_id", PROJECT_ID);
        serviceAccount.put("private_key_id", "test-key-id");
        serviceAccount.put("private_key", privateKey);
        serviceAccount.put("client_email", "firebase-adminsdk@moneymong-test.iam.gserviceaccount.com");
        serviceAccount.put("client_id", "1234567890");
        serviceAccount.put("token_uri", "https://oauth2.googleapis.com/token");

        byte[] json = new ObjectMapper().writeValueAsBytes(serviceAccount);
        return Base64.getEncoder().encodeToString(json);
    }
}
