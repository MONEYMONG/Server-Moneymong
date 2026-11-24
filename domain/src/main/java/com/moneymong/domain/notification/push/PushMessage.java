package com.moneymong.domain.notification.push;

import com.moneymong.domain.notification.entity.enums.NotificationType;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * 기기에 보낼 알림 내용.
 *
 * <p>{@code title}, {@code body}는 알림으로 띄우는 문구이고, {@code landing}은 알림을 눌렀을 때 이동할 화면이다(없으면 앱만 연다).
 * 앱으로 보내는 값은 앱과 약속한 키만 쓴다. 새 값이 필요하면 {@link PushLanding}처럼 타입이 있는 필드로 추가한다.
 */
public record PushMessage(
        NotificationType type,
        String title,
        String body,
        PushLanding landing
) {

    public static final String TYPE_KEY = "type";
    public static final String TITLE_KEY = "title";
    public static final String BODY_KEY = "body";

    public PushMessage {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(body, "body");
    }

    public static PushMessage of(NotificationType type, String title, String body) {
        return new PushMessage(type, title, body, null);
    }

    public static PushMessage of(NotificationType type, String title, String body, PushLanding landing) {
        return new PushMessage(type, title, body, landing);
    }

    public PushMessage withLanding(PushLanding landing) {
        return new PushMessage(type, title, body, landing);
    }

    /**
     * 앱으로 보낼 data. 알림 종류({@code type})와 이동할 화면({@code screen} 등)을 담는다.
     * 키 순서를 고정해 저장되는 payload가 매번 같게 한다.
     */
    public Map<String, String> appData() {
        Map<String, String> result = new TreeMap<>();
        result.put(TYPE_KEY, type.name());
        if (landing != null) {
            result.putAll(landing.toData());
        }
        return result;
    }
}
