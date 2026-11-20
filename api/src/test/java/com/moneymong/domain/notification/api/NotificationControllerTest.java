package com.moneymong.domain.notification.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moneymong.domain.notification.entity.enums.DevicePlatform;
import com.moneymong.domain.notification.entity.enums.NotificationStatus;
import com.moneymong.domain.notification.service.DeviceTokenService;
import com.moneymong.domain.notification.service.NotificationService;
import com.moneymong.domain.notification.service.dto.request.RegisterDeviceTokenRequest;
import com.moneymong.domain.notification.service.dto.request.SendTestNotificationRequest;
import com.moneymong.domain.notification.service.dto.response.NotificationDeliveryResponse;
import com.moneymong.domain.notification.service.dto.response.TestNotificationResponse;
import com.moneymong.global.exception.custom.BusinessException;
import com.moneymong.global.exception.enums.ErrorCode;
import com.moneymong.support.ControllerTestSupport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.http.MediaType;

class NotificationControllerTest extends ControllerTestSupport {

    private static final String DEVICE_ID = "device-1";
    private static final String TOKEN = "fcm-token";

    @Mock
    private DeviceTokenService deviceTokenService;

    @Mock
    private NotificationService notificationService;

    @Override
    protected Object controller() {
        return new NotificationController(deviceTokenService, notificationService);
    }

    @Test
    @DisplayName("내 기기의 토큰을 등록한다.")
    void registerDevice() throws Exception {
        mockMvc.perform(put("/api/v1/notifications/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("deviceId", DEVICE_ID, "token", TOKEN, "platform", "IOS"))))
                .andExpect(status().isOk());

        ArgumentCaptor<RegisterDeviceTokenRequest> captor = ArgumentCaptor.forClass(RegisterDeviceTokenRequest.class);
        then(deviceTokenService).should().register(eq(USER_ID), captor.capture());
        assertThat(captor.getValue().getDeviceId()).isEqualTo(DEVICE_ID);
        assertThat(captor.getValue().getToken()).isEqualTo(TOKEN);
        assertThat(captor.getValue().getPlatform()).isEqualTo(DevicePlatform.IOS);
    }

    @Test
    @DisplayName("토큰 등록에 deviceId, token, platform이 빠지면 400을 반환한다.")
    void registerDeviceWithoutFields() throws Exception {
        mockMvc.perform(put("/api/v1/notifications/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("deviceId", "", "token", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GLOBAL-400"))
                .andExpect(jsonPath("$.messages.length()").value(3));

        then(deviceTokenService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("토큰이 512자를 넘으면 400을 반환한다.")
    void registerDeviceWithTooLongToken() throws Exception {
        mockMvc.perform(put("/api/v1/notifications/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("deviceId", DEVICE_ID, "token", "a".repeat(513), "platform", "ANDROID"))))
                .andExpect(status().isBadRequest());

        then(deviceTokenService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("내 기기의 토큰을 지운다.")
    void unregisterDevice() throws Exception {
        mockMvc.perform(delete("/api/v1/notifications/devices/{deviceId}", DEVICE_ID))
                .andExpect(status().isOk());

        then(deviceTokenService).should().unregister(USER_ID, DEVICE_ID);
    }

    @Test
    @DisplayName("본문 없이 테스트 알림을 요청하면 내 모든 기기로 보내고 기기별 결과를 반환한다.")
    void sendTestNotificationToAllDevices() throws Exception {
        given(notificationService.sendTest(eq(USER_ID), any(SendTestNotificationRequest.class)))
                .willReturn(new TestNotificationResponse(List.of(
                        new NotificationDeliveryResponse(DEVICE_ID, DevicePlatform.ANDROID, NotificationStatus.SUCCESS, null),
                        new NotificationDeliveryResponse("device-2", DevicePlatform.IOS, NotificationStatus.FAILED, "UNREGISTERED")
                )));

        mockMvc.perform(post("/api/v1/notifications/test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveries[0].deviceId").value(DEVICE_ID))
                .andExpect(jsonPath("$.deliveries[0].platform").value("ANDROID"))
                .andExpect(jsonPath("$.deliveries[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.deliveries[1].status").value("FAILED"))
                .andExpect(jsonPath("$.deliveries[1].errorCode").value("UNREGISTERED"));

        ArgumentCaptor<SendTestNotificationRequest> captor = ArgumentCaptor.forClass(SendTestNotificationRequest.class);
        then(notificationService).should().sendTest(eq(USER_ID), captor.capture());
        assertThat(captor.getValue().getDeviceId()).isNull();
    }

    @Test
    @DisplayName("deviceId를 주면 그 기기로만 테스트 알림을 보낸다.")
    void sendTestNotificationToOneDevice() throws Exception {
        given(notificationService.sendTest(eq(USER_ID), any(SendTestNotificationRequest.class)))
                .willReturn(new TestNotificationResponse(List.of()));

        mockMvc.perform(post("/api/v1/notifications/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("deviceId", DEVICE_ID))))
                .andExpect(status().isOk());

        ArgumentCaptor<SendTestNotificationRequest> captor = ArgumentCaptor.forClass(SendTestNotificationRequest.class);
        then(notificationService).should().sendTest(eq(USER_ID), captor.capture());
        assertThat(captor.getValue().getDeviceId()).isEqualTo(DEVICE_ID);
    }

    @Test
    @DisplayName("테스트 알림을 너무 자주 보내면 429를 반환한다.")
    void sendTestNotificationTooOften() throws Exception {
        given(notificationService.sendTest(eq(USER_ID), any(SendTestNotificationRequest.class)))
                .willThrow(new BusinessException(ErrorCode.NOTIFICATION_TEST_TOO_MANY_REQUESTS));

        mockMvc.perform(post("/api/v1/notifications/test"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("NOTIFICATION-002"));
    }
}
