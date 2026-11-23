package com.moneymong.global.notification.fcm;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.FcmOptions;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.moneymong.domain.notification.entity.enums.DevicePlatform;
import com.moneymong.domain.notification.push.PushMessage;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * {@link PushMessage}를 FCM HTTP v1 메시지로 바꾼다. 플랫폼마다 형식이 다르다.
 *
 * <ul>
 *   <li>Android: data 메시지만 보낸다. title·body도 data에 넣는다.
 *       앱이 포그라운드·백그라운드 모두 직접 받아 알림 채널, 로그인 여부 확인, 화면 이동을 처리하기 때문이다.</li>
 *   <li>iOS: notification과 data를 함께 보낸다. data만 보내면 무음 푸시로 처리돼 알림이 뜨지 않는다.
 *       data는 APNs payload의 최상위 키로 전달된다.</li>
 * </ul>
 */
public class FcmMessageFactory {

    // 기기가 꺼져 있을 때 FCM이 보관했다가 전달하는 시간
    static final Duration TIME_TO_LIVE = Duration.ofDays(1);
    static final String DEFAULT_SOUND = "default";

    public Message create(String token, DevicePlatform platform, PushMessage message) {
        Message.Builder builder = switch (platform) {
            case ANDROID -> androidMessage(message);
            case IOS -> iosMessage(message);
        };
        return builder
                .setToken(token)
                // Firebase 콘솔 리포트에서 알림 종류별로 볼 수 있게 한다.
                .setFcmOptions(FcmOptions.withAnalyticsLabel(message.type().name().toLowerCase(Locale.ROOT)))
                .build();
    }

    private Message.Builder androidMessage(PushMessage message) {
        Map<String, String> data = new LinkedHashMap<>(message.appData());
        data.put(PushMessage.TITLE_KEY, message.title());
        data.put(PushMessage.BODY_KEY, message.body());

        return Message.builder()
                .putAllData(data)
                .setAndroidConfig(AndroidConfig.builder()
                        // data 메시지도 앱이 꺼져 있을 때 바로 깨워 알림을 띄울 수 있게 높은 우선순위로 보낸다.
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .setTtl(TIME_TO_LIVE.toMillis())
                        .build());
    }

    private Message.Builder iosMessage(PushMessage message) {
        return Message.builder()
                .setNotification(Notification.builder()
                        .setTitle(message.title())
                        .setBody(message.body())
                        .build())
                .putAllData(message.appData())
                .setApnsConfig(ApnsConfig.builder()
                        .setAps(Aps.builder()
                                .setSound(DEFAULT_SOUND)
                                .build())
                        .build());
    }
}
