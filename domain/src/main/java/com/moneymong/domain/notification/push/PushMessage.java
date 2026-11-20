package com.moneymong.domain.notification.push;

import com.moneymong.domain.notification.entity.enums.NotificationType;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * 기기에 보낼 알림 내용.
 *
 * <p>{@code title}, {@code body}는 OS가 알림으로 띄우는 문구이고, {@code data}는 앱이 알림을 눌렀을 때 쓰는 값이다(예: link).
 * FCM data는 문자열만 담을 수 있다.
 */
public record PushMessage(
        NotificationType type,
        String title,
        String body,
        Map<String, String> data
) {

    public static final String TYPE_KEY = "type";

    public PushMessage {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(body, "body");
        data = data == null ? Map.of() : Map.copyOf(data);
        if (data.containsKey(TYPE_KEY)) {
            throw new IllegalArgumentException("data의 type은 알림 종류로 채워지므로 직접 넣을 수 없다.");
        }
    }

    public static PushMessage of(NotificationType type, String title, String body) {
        return new PushMessage(type, title, body, Map.of());
    }

    /**
     * 앱으로 보낼 data. 알림 종류를 {@code type}으로 함께 넣는다. 키 순서를 고정해 저장되는 payload가 매번 같게 한다.
     */
    public Map<String, String> dataWithType() {
        Map<String, String> result = new TreeMap<>(data);
        result.put(TYPE_KEY, type.name());
        return result;
    }
}
