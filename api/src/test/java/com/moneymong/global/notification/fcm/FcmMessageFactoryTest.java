package com.moneymong.global.notification.fcm;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.json.gson.GsonFactory;
import com.google.firebase.messaging.Message;
import com.moneymong.domain.notification.entity.enums.NotificationType;
import com.moneymong.domain.notification.push.PushMessage;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FcmMessageFactoryTest {

    private static final String CHANNEL_ID = "moneymong_default";
    private static final String TOKEN = "fcm-token";

    private final FcmMessageFactory factory = new FcmMessageFactory(CHANNEL_ID);

    @Test
    @DisplayName("notification, data, Android·iOS 설정, analytics label을 담은 FCM 메시지를 만든다.")
    void create() throws Exception {
        // given
        PushMessage message = new PushMessage(NotificationType.TEST, "제목", "본문", Map.of("link", "moneymong://home"));

        // when
        Message fcmMessage = factory.create(TOKEN, message);

        // then: FCM으로 나가는 JSON 형태로 검증한다.
        JsonNode json = new ObjectMapper().readTree(GsonFactory.getDefaultInstance().toString(fcmMessage));
        assertThat(json.get("token").asText()).isEqualTo(TOKEN);
        assertThat(json.at("/notification/title").asText()).isEqualTo("제목");
        assertThat(json.at("/notification/body").asText()).isEqualTo("본문");
        assertThat(json.at("/data/link").asText()).isEqualTo("moneymong://home");
        assertThat(json.at("/data/type").asText()).isEqualTo("TEST");
        assertThat(json.at("/android/priority").asText()).isEqualTo("high");
        assertThat(json.at("/android/ttl").asText()).isEqualTo("86400s");
        assertThat(json.at("/android/notification/channel_id").asText()).isEqualTo(CHANNEL_ID);
        assertThat(json.at("/apns/payload/aps/sound").asText()).isEqualTo("default");
        assertThat(json.at("/fcm_options/analytics_label").asText()).isEqualTo("test");
    }
}
