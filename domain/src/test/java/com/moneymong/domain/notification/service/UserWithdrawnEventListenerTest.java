package com.moneymong.domain.notification.service;

import static org.mockito.BDDMockito.then;

import com.moneymong.domain.user.event.UserWithdrawnEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserWithdrawnEventListenerTest {

    private static final Long USER_ID = 1L;

    @Mock
    private DeviceTokenService deviceTokenService;

    @InjectMocks
    private UserWithdrawnEventListener userWithdrawnEventListener;

    @Test
    @DisplayName("회원이 탈퇴하면 그 회원의 디바이스 토큰을 모두 지운다.")
    void deleteDeviceTokens() {
        // when
        userWithdrawnEventListener.deleteDeviceTokens(new UserWithdrawnEvent(USER_ID));

        // then
        then(deviceTokenService).should().deleteAll(USER_ID);
    }
}
