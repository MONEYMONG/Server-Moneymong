package com.moneymong.domain.notification.push;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 알림을 눌렀을 때 이동할 화면과 그 화면에 필요한 값.
 *
 * @param screen   이동할 화면
 * @param agencyId 장부를 열 소속 ID
 */
public record PushLanding(
        PushScreen screen,
        Long agencyId
) {

    public static final String SCREEN_KEY = "screen";
    public static final String AGENCY_ID_KEY = "agencyId";

    public PushLanding {
        Objects.requireNonNull(screen, "screen");
        if (screen == PushScreen.LEDGER) {
            Objects.requireNonNull(agencyId, "agencyId");
        }
    }

    public static PushLanding ledger(Long agencyId) {
        return new PushLanding(PushScreen.LEDGER, agencyId);
    }

    /**
     * FCM data에 넣을 값. FCM data는 문자열만 담을 수 있어 ID도 문자열로 바꾼다.
     */
    public Map<String, String> toData() {
        Map<String, String> data = new LinkedHashMap<>();
        data.put(SCREEN_KEY, screen.name());
        if (agencyId != null) {
            data.put(AGENCY_ID_KEY, agencyId.toString());
        }
        return data;
    }
}
