package com.moneymong.global.notification.fcm;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.json.gson.GsonFactory;
import com.google.firebase.messaging.Message;
import com.moneymong.domain.notification.entity.enums.DevicePlatform;
import com.moneymong.domain.notification.entity.enums.NotificationType;
import com.moneymong.domain.notification.push.PushLanding;
import com.moneymong.domain.notification.push.PushMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FcmMessageFactoryTest {

    private static final String TOKEN = "fcm-token";
    private static final PushMessage MESSAGE = PushMessage.of(NotificationType.TEST, "제목", "본문", PushLanding.ledger(7L));

    private final FcmMessageFactory factory = new FcmMessageFactory();

    @Test
    @DisplayName("Android는 title·body·이동할 화면을 모두 data에 담은 data 메시지로 보낸다.")
    void android() throws Exception {
        // when
        JsonNode json = toJson(factory.create(TOKEN, DevicePlatform.ANDROID, MESSAGE));

        // then
        assertThat(json.get("token").asText()).isEqualTo(TOKEN);
        assertThat(json.has("notification")).isFalse();
        assertThat(json.at("/data/title").asText()).isEqualTo("제목");
        assertThat(json.at("/data/body").asText()).isEqualTo("본문");
        assertThat(json.at("/data/type").asText()).isEqualTo("TEST");
        assertThat(json.at("/data/screen").asText()).isEqualTo("LEDGER");
        assertThat(json.at("/data/agencyId").asText()).isEqualTo("7");
        assertThat(json.at("/android/priority").asText()).isEqualTo("high");
        assertThat(json.at("/android/ttl").asText()).isEqualTo("86400s");
        assertThat(json.at("/android").has("notification")).isFalse();
        assertThat(json.has("apns")).isFalse();
        assertThat(json.at("/fcm_options/analytics_label").asText()).isEqualTo("test");
    }

    @Test
    @DisplayName("iOS는 notification으로 문구를 보내고 이동할 화면은 data에 담는다.")
    void ios() throws Exception {
        // when
        JsonNode json = toJson(factory.create(TOKEN, DevicePlatform.IOS, MESSAGE));

        // then
        assertThat(json.get("token").asText()).isEqualTo(TOKEN);
        assertThat(json.at("/notification/title").asText()).isEqualTo("제목");
        assertThat(json.at("/notification/body").asText()).isEqualTo("본문");
        assertThat(json.at("/data/type").asText()).isEqualTo("TEST");
        assertThat(json.at("/data/screen").asText()).isEqualTo("LEDGER");
        assertThat(json.at("/data/agencyId").asText()).isEqualTo("7");
        assertThat(json.at("/data").has("title")).isFalse();
        assertThat(json.at("/apns/payload/aps/sound").asText()).isEqualTo("default");
        assertThat(json.has("android")).isFalse();
        assertThat(json.at("/fcm_options/analytics_label").asText()).isEqualTo("test");
    }

    @Test
    @DisplayName("이동할 화면이 없으면 screen을 보내지 않는다(앱만 연다).")
    void withoutLanding() throws Exception {
        // when
        JsonNode json = toJson(factory.create(TOKEN, DevicePlatform.ANDROID, PushMessage.of(NotificationType.TEST, "제목", "본문")));

        // then
        assertThat(json.at("/data").has("screen")).isFalse();
        assertThat(json.at("/data").has("agencyId")).isFalse();
    }

    private JsonNode toJson(Message message) throws Exception {
        // FCM으로 나가는 JSON 형태로 검증한다.
        return new ObjectMapper().readTree(GsonFactory.getDefaultInstance().toString(message));
    }
}
