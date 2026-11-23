package com.moneymong.domain.notification.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moneymong.domain.notification.entity.enums.NotificationType;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PushMessageTest {

    private static final String TITLE = "제목";
    private static final String BODY = "본문";

    @Test
    @DisplayName("앱으로 보낼 data에 알림 종류와 이동할 화면을 함께 넣고 키 순서를 고정한다.")
    void appData() {
        // given
        PushMessage message = new PushMessage(
                NotificationType.TEST, TITLE, BODY, PushLanding.ledger(7L), Map.of("extra", "value")
        );

        // when
        Map<String, String> data = message.appData();

        // then
        assertThat(data).containsExactly(
                Map.entry(PushLanding.AGENCY_ID_KEY, "7"),
                Map.entry("extra", "value"),
                Map.entry(PushLanding.SCREEN_KEY, "LEDGER"),
                Map.entry(PushMessage.TYPE_KEY, "TEST")
        );
    }

    @Test
    @DisplayName("이동할 화면과 data가 없으면 type만 보낸다(앱만 연다).")
    void withoutLandingAndData() {
        // when
        PushMessage message = PushMessage.of(NotificationType.TEST, TITLE, BODY);

        // then
        assertThat(message.landing()).isNull();
        assertThat(message.data()).isEmpty();
        assertThat(message.appData()).containsExactly(Map.entry(PushMessage.TYPE_KEY, "TEST"));
    }

    @Test
    @DisplayName("withLanding은 나머지 내용을 그대로 두고 이동할 화면만 바꾼다.")
    void withLanding() {
        // given
        PushMessage message = PushMessage.of(NotificationType.TEST, TITLE, BODY);

        // when
        PushMessage withLanding = message.withLanding(PushLanding.ledger(3L));

        // then
        assertThat(withLanding).isEqualTo(PushMessage.of(NotificationType.TEST, TITLE, BODY, PushLanding.ledger(3L)));
        assertThat(message.landing()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"type", "title", "body", "screen", "agencyId"})
    @DisplayName("서버가 채우는 키는 data에 직접 넣을 수 없다.")
    void rejectReservedKeys(String key) {
        assertThatThrownBy(() -> new PushMessage(NotificationType.TEST, TITLE, BODY, null, Map.of(key, "value")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("장부 화면으로 이동하려면 agencyId가 필요하다.")
    void ledgerRequiresAgencyId() {
        assertThatThrownBy(() -> PushLanding.ledger(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PushLanding(null, 1L)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("만든 뒤에 원본 data를 바꿔도 메시지는 바뀌지 않는다.")
    void copyData() {
        // given
        Map<String, String> data = new HashMap<>(Map.of("extra", "before"));
        PushMessage message = new PushMessage(NotificationType.TEST, TITLE, BODY, null, data);

        // when
        data.put("extra", "after");

        // then
        assertThat(message.data()).containsEntry("extra", "before");
    }

    @Test
    @DisplayName("종류, 제목, 본문은 비울 수 없다.")
    void requireFields() {
        assertThatThrownBy(() -> PushMessage.of(null, TITLE, BODY)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PushMessage.of(NotificationType.TEST, null, BODY)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PushMessage.of(NotificationType.TEST, TITLE, null)).isInstanceOf(NullPointerException.class);
    }
}
