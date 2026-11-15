package com.moneymong.domain.user.service.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneymong.domain.user.service.dto.request.LoginRequest;
import com.moneymong.domain.user.entity.User;
import com.moneymong.domain.user.repository.UserRepository;
import com.moneymong.global.exception.custom.NotFoundException;
import com.moneymong.global.exception.enums.ErrorCode;
import com.moneymong.domain.user.oauth.dto.OAuthUserDataRequest;
import com.moneymong.domain.user.oauth.dto.OAuthUserDataResponse;
import com.moneymong.domain.user.oauth.handler.OAuthAuthenticationHandler;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OAuthServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private OAuthAuthenticationHandler kakaoHandler;

    @Mock
    private OAuthAuthenticationHandler appleHandler;

    @Mock
    private UserRepository userRepository;

    private OAuthService oAuthService;

    @BeforeEach
    void setUp() {
        given(kakaoHandler.getAuthProvider()).willReturn(OAuthProvider.KAKAO);
        given(appleHandler.getAuthProvider()).willReturn(OAuthProvider.APPLE);
        oAuthService = new OAuthService(List.of(kakaoHandler, appleHandler), userRepository);
    }

    private LoginRequest loginRequest(String provider) {
        // LoginRequest는 setter/생성자가 없어 실제 요청과 같은 방식(JSON 역직렬화)으로 만든다.
        return new ObjectMapper().convertValue(
                Map.of("provider", provider, "accessToken", "oauth-token", "code", "auth-code", "name", "몽몽"),
                LoginRequest.class
        );
    }

    private User user(String provider) {
        return User.builder()
                .id(USER_ID)
                .email("test@example.com")
                .nickname("몽몽")
                .provider(provider)
                .oauthId("oauth-id")
                .build();
    }

    @Test
    @DisplayName("카카오 로그인 요청은 카카오 핸들러로 전달되고 OAuth 유저 정보를 반환한다.")
    void loginWithKakao() {
        OAuthUserDataResponse kakaoUser = OAuthUserDataResponse.builder()
                .provider("KAKAO")
                .oauthId("kakao-id")
                .email("kakao@example.com")
                .nickname("카카오몽")
                .build();
        given(kakaoHandler.getOAuthUserData(any(OAuthUserDataRequest.class))).willReturn(kakaoUser);

        OAuthUserDataResponse response = oAuthService.login(loginRequest("KAKAO"));

        assertThat(response).isSameAs(kakaoUser);
        ArgumentCaptor<OAuthUserDataRequest> captor = ArgumentCaptor.forClass(OAuthUserDataRequest.class);
        then(kakaoHandler).should().getOAuthUserData(captor.capture());
        assertThat(captor.getValue().getAccessToken()).isEqualTo("oauth-token");
        assertThat(captor.getValue().getCode()).isEqualTo("auth-code");
        assertThat(captor.getValue().getName()).isEqualTo("몽몽");
        then(appleHandler).should(never()).getOAuthUserData(any());
    }

    @Test
    @DisplayName("애플 로그인 요청은 애플 핸들러로 전달된다.")
    void loginWithApple() {
        OAuthUserDataResponse appleUser = OAuthUserDataResponse.builder()
                .provider("APPLE")
                .oauthId("apple-id")
                .appleRefreshToken("apple-refresh")
                .build();
        given(appleHandler.getOAuthUserData(any(OAuthUserDataRequest.class))).willReturn(appleUser);

        OAuthUserDataResponse response = oAuthService.login(loginRequest("APPLE"));

        assertThat(response.getProvider()).isEqualTo("APPLE");
        assertThat(response.getAppleRefreshToken()).isEqualTo("apple-refresh");
        then(kakaoHandler).should(never()).getOAuthUserData(any());
    }

    @Test
    @DisplayName("지원하지 않는 로그인 수단이면 NotFoundException이 발생하고 어떤 핸들러도 호출하지 않는다.")
    void loginWithInvalidProvider() {
        assertThatThrownBy(() -> oAuthService.login(loginRequest("GOOGLE")))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_PROVIDER);

        then(kakaoHandler).should(never()).getOAuthUserData(any());
        then(appleHandler).should(never()).getOAuthUserData(any());
    }

    @Test
    @DisplayName("회원의 로그인 수단에 맞는 핸들러로 연동을 해제한다.")
    void revoke() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user("APPLE")));

        oAuthService.revoke(USER_ID);

        then(appleHandler).should().unlink(USER_ID);
        then(kakaoHandler).should(never()).unlink(anyLong());
    }

    @Test
    @DisplayName("존재하지 않는 회원의 연동 해제를 요청하면 NotFoundException이 발생한다.")
    void revokeUserNotFound() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> oAuthService.revoke(USER_ID))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_NOT_FOUND);

        then(kakaoHandler).should(never()).unlink(anyLong());
        then(appleHandler).should(never()).unlink(anyLong());
    }
}
