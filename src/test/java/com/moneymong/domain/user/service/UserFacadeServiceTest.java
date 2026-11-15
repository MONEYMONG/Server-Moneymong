package com.moneymong.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;

import com.moneymong.domain.agency.service.AgencyUserService;
import com.moneymong.domain.user.service.dto.request.LoginRequest;
import com.moneymong.domain.user.service.dto.response.LoginSuccessResponse;
import com.moneymong.global.exception.custom.BusinessException;
import com.moneymong.global.exception.custom.NotFoundException;
import com.moneymong.global.exception.enums.ErrorCode;
import com.moneymong.domain.auth.dto.AuthUserInfo;
import com.moneymong.domain.user.oauth.dto.OAuthUserDataResponse;
import com.moneymong.domain.user.oauth.dto.OAuthUserInfo;
import com.moneymong.domain.user.service.oauth.OAuthService;
import com.moneymong.domain.auth.dto.Tokens;
import com.moneymong.domain.auth.service.TokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserFacadeServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private UserService userService;
    @Mock
    private TokenService tokenService;
    @Mock
    private OAuthService oAuthService;
    @Mock
    private UserUniversityService userUniversityService;
    @Mock
    private AgencyUserService agencyUserService;

    @InjectMocks
    private UserFacadeService userFacadeService;

    @ParameterizedTest(name = "학교 정보 존재 여부: {0}")
    @ValueSource(booleans = {true, false})
    @DisplayName("로그인하면 토큰과 학교 정보 등록 여부를 반환한다.")
    void login(boolean schoolInfoExists) {
        // given
        LoginRequest loginRequest = new LoginRequest();
        OAuthUserDataResponse oAuthUserData = OAuthUserDataResponse.builder()
                .provider("KAKAO")
                .oauthId("oauth-123")
                .nickname("머니몽")
                .email("user@example.com")
                .build();
        AuthUserInfo authUserInfo = AuthUserInfo.from(USER_ID, "머니몽", UserService.DEFAULT_ROLE);
        given(oAuthService.login(loginRequest)).willReturn(oAuthUserData);
        given(userService.getOrRegister(any(OAuthUserInfo.class))).willReturn(authUserInfo);
        given(tokenService.createTokens(authUserInfo))
                .willReturn(new Tokens("access-token-placeholder", "refresh-token-placeholder"));
        given(userUniversityService.exists(USER_ID)).willReturn(schoolInfoExists);

        // when
        LoginSuccessResponse response = userFacadeService.login(loginRequest);

        // then
        assertThat(response.getAccessToken()).isEqualTo("access-token-placeholder");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token-placeholder");
        assertThat(response.isLoginSuccess()).isTrue();
        assertThat(response.isSchoolInfoExist()).isEqualTo(schoolInfoExists);
        assertThat(response.isSchoolInfoProvided()).isEqualTo(schoolInfoExists);

        ArgumentCaptor<OAuthUserInfo> captor = ArgumentCaptor.forClass(OAuthUserInfo.class);
        then(userService).should().getOrRegister(captor.capture());
        assertThat(captor.getValue().getProvider()).isEqualTo("KAKAO");
        assertThat(captor.getValue().getOauthId()).isEqualTo("oauth-123");
        assertThat(captor.getValue().getNickname()).isEqualTo("머니몽");
    }

    @Test
    @DisplayName("회원 탈퇴 시 OAuth 연동 해제, 회원, 대학 정보, 소속 정보 순서로 삭제한다.")
    void delete() {
        // when
        userFacadeService.delete(USER_ID);

        // then
        InOrder inOrder = inOrder(oAuthService, userService, userUniversityService, agencyUserService);
        inOrder.verify(oAuthService).revoke(USER_ID);
        inOrder.verify(userService).delete(USER_ID);
        inOrder.verify(userUniversityService).delete(USER_ID);
        inOrder.verify(agencyUserService).deleteAll(USER_ID);
    }

    @Test
    @DisplayName("존재하지 않는 회원을 탈퇴시키면 예외가 발생하고 이후 정보는 삭제하지 않는다.")
    void deleteNotFoundUser() {
        // given
        willThrow(new NotFoundException(ErrorCode.USER_NOT_FOUND)).given(userService).delete(USER_ID);

        // when & then
        assertThatThrownBy(() -> userFacadeService.delete(USER_ID))
                .isInstanceOf(NotFoundException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
        then(userUniversityService).should(never()).delete(anyLong());
        then(agencyUserService).should(never()).deleteAll(anyLong());
    }
}
