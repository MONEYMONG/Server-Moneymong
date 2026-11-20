package com.moneymong.domain.notification.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moneymong.domain.notification.entity.enums.NotificationType;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PushMessageTest {

    private static final String TITLE = "제목";
    private static final String BODY = "본문";

    @Test
    @DisplayName("앱으로 보낼 data에 알림 종류를 type으로 함께 넣고 키 순서를 고정한다.")
    void dataWithType() {
        // given
        PushMessage message = new PushMessage(NotificationType.TEST, TITLE, BODY, Map.of("link", "moneymong://ledger/1", "agencyId", "7"));

        // when
        Map<String, String> data = message.dataWithType();

        // then
        assertThat(data).containsExactly(
                Map.entry("agencyId", "7"),
                Map.entry("link", "moneymong://ledger/1"),
                Map.entry(PushMessage.TYPE_KEY, "TEST")
        );
    }

    @Test
    @DisplayName("data를 비우면 type만 보낸다.")
    void withoutData() {
        // when
        PushMessage message = new PushMessage(NotificationType.TEST, TITLE, BODY, null);

        // then
        assertThat(message.data()).isEmpty();
        assertThat(message.dataWithType()).containsExactly(Map.entry(PushMessage.TYPE_KEY, "TEST"));
    }

    @Test
    @DisplayName("data에 type을 직접 넣을 수 없다.")
    void rejectTypeInData() {
        assertThatThrownBy(() -> new PushMessage(NotificationType.TEST, TITLE, BODY, Map.of(PushMessage.TYPE_KEY, "OTHER")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("만든 뒤에 원본 data를 바꿔도 메시지는 바뀌지 않는다.")
    void copyData() {
        // given
        Map<String, String> data = new HashMap<>(Map.of("link", "before"));
        PushMessage message = new PushMessage(NotificationType.TEST, TITLE, BODY, data);

        // when
        data.put("link", "after");

        // then
        assertThat(message.data()).containsEntry("link", "before");
    }

    @Test
    @DisplayName("종류, 제목, 본문은 비울 수 없다.")
    void requireFields() {
        assertThatThrownBy(() -> PushMessage.of(null, TITLE, BODY)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PushMessage.of(NotificationType.TEST, null, BODY)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PushMessage.of(NotificationType.TEST, TITLE, null)).isInstanceOf(NullPointerException.class);
    }
}
