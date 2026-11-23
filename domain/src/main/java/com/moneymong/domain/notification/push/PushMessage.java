package com.moneymong.domain.notification.push;

import com.moneymong.domain.notification.entity.enums.NotificationType;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * 기기에 보낼 알림 내용.
 *
 * <p>{@code title}, {@code body}는 알림으로 띄우는 문구이고, {@code landing}은 알림을 눌렀을 때 이동할 화면이다(없으면 앱만 연다).
 * {@code data}는 그 밖에 앱이 쓰는 값이다. FCM data는 문자열만 담을 수 있다.
 */
public record PushMessage(
        NotificationType type,
        String title,
        String body,
        PushLanding landing,
        Map<String, String> data
) {

    public static final String TYPE_KEY = "type";
    public static final String TITLE_KEY = "title";
    public static final String BODY_KEY = "body";

    /**
     * 서버가 채우는 data 키. Android는 data 메시지로 보내 title·body도 data에 넣으므로 함께 막는다.
     */
    static final Set<String> RESERVED_DATA_KEYS = Set.of(
            TYPE_KEY, TITLE_KEY, BODY_KEY, PushLanding.SCREEN_KEY, PushLanding.AGENCY_ID_KEY
    );

    public PushMessage {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(body, "body");
        data = data == null ? Map.of() : Map.copyOf(data);
        for (String key : data.keySet()) {
            if (RESERVED_DATA_KEYS.contains(key)) {
                throw new IllegalArgumentException("data의 " + key + "는 서버가 채우므로 직접 넣을 수 없다.");
            }
        }
    }

    public static PushMessage of(NotificationType type, String title, String body) {
        return new PushMessage(type, title, body, null, Map.of());
    }

    public static PushMessage of(NotificationType type, String title, String body, PushLanding landing) {
        return new PushMessage(type, title, body, landing, Map.of());
    }

    public PushMessage withLanding(PushLanding landing) {
        return new PushMessage(type, title, body, landing, data);
    }

    /**
     * 앱으로 보낼 data. 알림 종류({@code type})와 이동할 화면({@code screen} 등)을 함께 넣는다.
     * 키 순서를 고정해 저장되는 payload가 매번 같게 한다.
     */
    public Map<String, String> appData() {
        Map<String, String> result = new TreeMap<>(data);
        result.put(TYPE_KEY, type.name());
        if (landing != null) {
            result.putAll(landing.toData());
        }
        return result;
    }
}
