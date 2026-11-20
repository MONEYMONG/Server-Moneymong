package com.moneymong.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;

import com.moneymong.domain.notification.entity.DeviceToken;
import com.moneymong.domain.notification.entity.enums.DevicePlatform;
import com.moneymong.domain.notification.repository.DeviceTokenRepository;
import com.moneymong.domain.notification.service.dto.request.RegisterDeviceTokenRequest;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeviceTokenServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final String DEVICE_ID = "device-1";
    private static final String OTHER_DEVICE_ID = "device-2";
    private static final String TOKEN = "fcm-token-new";
    private static final String OLD_TOKEN = "fcm-token-old";
    private static final ZonedDateTime REGISTERED_AT = ZonedDateTime.parse("2026-01-01T00:00:00+09:00");

    @Mock
    private DeviceTokenRepository deviceTokenRepository;

    @InjectMocks
    private DeviceTokenService deviceTokenService;

    @Test
    @DisplayName("처음 보는 기기면 토큰을 새로 저장한다.")
    void registerNewDevice() {
        // given
        given(deviceTokenRepository.findByToken(TOKEN)).willReturn(Optional.empty());
        given(deviceTokenRepository.findByDeviceId(DEVICE_ID)).willReturn(Optional.empty());

        // when
        deviceTokenService.register(USER_ID, request(DEVICE_ID, TOKEN, DevicePlatform.ANDROID));

        // then
        ArgumentCaptor<DeviceToken> captor = ArgumentCaptor.forClass(DeviceToken.class);
        then(deviceTokenRepository).should().save(captor.capture());
        DeviceToken saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getDeviceId()).isEqualTo(DEVICE_ID);
        assertThat(saved.getToken()).isEqualTo(TOKEN);
        assertThat(saved.getPlatform()).isEqualTo(DevicePlatform.ANDROID);
        assertThat(saved.getLastActiveAt()).isNotNull();
    }

    @Test
    @DisplayName("이미 등록된 기기면 새 행을 만들지 않고 토큰과 등록 시각을 갱신한다.")
    void refreshExistingDevice() {
        // given
        DeviceToken existing = DeviceToken.of(USER_ID, DEVICE_ID, OLD_TOKEN, DevicePlatform.ANDROID, REGISTERED_AT);
        given(deviceTokenRepository.findByToken(TOKEN)).willReturn(Optional.empty());
        given(deviceTokenRepository.findByDeviceId(DEVICE_ID)).willReturn(Optional.of(existing));

        // when
        deviceTokenService.register(USER_ID, request(DEVICE_ID, TOKEN, DevicePlatform.ANDROID));

        // then
        assertThat(existing.getToken()).isEqualTo(TOKEN);
        assertThat(existing.getLastActiveAt()).isAfter(REGISTERED_AT);
        then(deviceTokenRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("같은 기기에서 다른 계정으로 로그인하면 기기의 주인을 새 계정으로 바꾼다.")
    void changeOwnerOfDevice() {
        // given
        DeviceToken existing = DeviceToken.of(OTHER_USER_ID, DEVICE_ID, TOKEN, DevicePlatform.IOS, REGISTERED_AT);
        given(deviceTokenRepository.findByToken(TOKEN)).willReturn(Optional.of(existing));
        given(deviceTokenRepository.findByDeviceId(DEVICE_ID)).willReturn(Optional.of(existing));

        // when
        deviceTokenService.register(USER_ID, request(DEVICE_ID, TOKEN, DevicePlatform.IOS));

        // then
        assertThat(existing.getUserId()).isEqualTo(USER_ID);
        then(deviceTokenRepository).should(never()).delete(any());
        then(deviceTokenRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("같은 토큰이 다른 기기 행에 있으면 그 행을 먼저 지우고 바로 반영한 뒤 저장한다.")
    void moveTokenFromOtherDevice() {
        // given
        DeviceToken stale = DeviceToken.of(USER_ID, OTHER_DEVICE_ID, TOKEN, DevicePlatform.ANDROID, REGISTERED_AT);
        given(deviceTokenRepository.findByToken(TOKEN)).willReturn(Optional.of(stale));
        given(deviceTokenRepository.findByDeviceId(DEVICE_ID)).willReturn(Optional.empty());

        // when
        deviceTokenService.register(USER_ID, request(DEVICE_ID, TOKEN, DevicePlatform.ANDROID));

        // then
        InOrder inOrder = inOrder(deviceTokenRepository);
        inOrder.verify(deviceTokenRepository).delete(stale);
        inOrder.verify(deviceTokenRepository).flush();
        inOrder.verify(deviceTokenRepository).save(any(DeviceToken.class));
    }

    @Test
    @DisplayName("기기 토큰을 지울 때는 내 기기만 지운다.")
    void unregister() {
        // when
        deviceTokenService.unregister(USER_ID, DEVICE_ID);

        // then
        then(deviceTokenRepository).should().deleteByUserIdAndDeviceId(USER_ID, DEVICE_ID);
    }

    @Test
    @DisplayName("회원의 모든 기기 토큰을 지운다.")
    void deleteAll() {
        // when
        deviceTokenService.deleteAll(USER_ID);

        // then
        then(deviceTokenRepository).should().deleteAllByUserId(USER_ID);
    }

    private RegisterDeviceTokenRequest request(String deviceId, String token, DevicePlatform platform) {
        return new RegisterDeviceTokenRequest(deviceId, token, platform);
    }
}
