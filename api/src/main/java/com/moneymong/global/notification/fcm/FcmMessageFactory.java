package com.moneymong.global.notification.fcm;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.FcmOptions;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.moneymong.domain.notification.push.PushMessage;
import java.time.Duration;
import java.util.Locale;

/**
 * {@link PushMessage}를 FCM HTTP v1 메시지로 바꾼다.
 *
 * <p>notification(OS가 띄우는 문구)과 data(앱이 쓰는 값)를 함께 보낸다.
 * Android는 채널 ID와 높은 우선순위, iOS는 기본 알림음을 지정한다.
 */
public class FcmMessageFactory {

    // 기기가 꺼져 있을 때 FCM이 보관했다가 전달하는 시간
    static final Duration TIME_TO_LIVE = Duration.ofDays(1);
    static final String DEFAULT_SOUND = "default";

    private final String androidChannelId;

    public FcmMessageFactory(String androidChannelId) {
        this.androidChannelId = androidChannelId;
    }

    public Message create(String token, PushMessage message) {
        return Message.builder()
                .setToken(token)
                .setNotification(Notification.builder()
                        .setTitle(message.title())
                        .setBody(message.body())
                        .build())
                .putAllData(message.dataWithType())
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .setTtl(TIME_TO_LIVE.toMillis())
                        .setNotification(AndroidNotification.builder()
                                .setChannelId(androidChannelId)
                                .build())
                        .build())
                .setApnsConfig(ApnsConfig.builder()
                        .setAps(Aps.builder()
                                .setSound(DEFAULT_SOUND)
                                .build())
                        .build())
                // Firebase 콘솔 리포트에서 알림 종류별로 볼 수 있게 한다.
                .setFcmOptions(FcmOptions.withAnalyticsLabel(message.type().name().toLowerCase(Locale.ROOT)))
                .build();
    }
}
