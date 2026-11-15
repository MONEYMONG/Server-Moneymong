package com.moneymong.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.never;

import com.moneymong.domain.user.service.dto.response.UserProfileResponse;
import com.moneymong.domain.user.entity.AppleUser;
import com.moneymong.domain.user.entity.User;
import com.moneymong.domain.user.entity.UserUniversity;
import com.moneymong.domain.user.repository.AppleUserRepository;
import com.moneymong.domain.user.repository.UserRepository;
import com.moneymong.domain.user.repository.UserUniversityRepository;
import com.moneymong.global.exception.custom.BusinessException;
import com.moneymong.global.exception.custom.NotFoundException;
import com.moneymong.global.exception.enums.ErrorCode;
import com.moneymong.global.security.oauth.dto.AuthUserInfo;
import com.moneymong.global.security.oauth.dto.OAuthUserInfo;
import com.moneymong.global.security.token.entity.RefreshToken;
import com.moneymong.global.security.token.repository.RefreshTokenRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final Long USER_ID = 1L;
    private static final String PROVIDER = "KAKAO";
    private static final String OAUTH_ID = "oauth-123";

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserUniversityRepository userUniversityRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private AppleUserRepository appleUserRepository;

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("이미 가입한 회원은 새로 저장하지 않고 기존 회원 정보를 반환한다.")
    void getOrRegisterExistingUser() {
        // given
        User user = user(USER_ID, "머니몽");
        given(userRepository.findByUserIdByProviderAndOauthId(PROVIDER, OAUTH_ID)).willReturn(Optional.of(user));

        // when
        AuthUserInfo result = userService.getOrRegister(oAuthUserInfo("머니몽", null));

        // then
        assertThat(result.getUserId()).isEqualTo(USER_ID);
        assertThat(result.getNickname()).isEqualTo("머니몽");
        assertThat(result.getRole()).isEqualTo(UserService.DEFAULT_ROLE);
        then(userRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("처음 로그인한 회원은 새로 저장한 뒤 회원 정보를 반환한다.")
    void getOrRegisterNewUser() {
        // given
        given(userRepository.findByUserIdByProviderAndOauthId(PROVIDER, OAUTH_ID)).willReturn(Optional.empty());
        givenUserSaveAssignsId();

        // when
        AuthUserInfo result = userService.getOrRegister(oAuthUserInfo("머니몽", null));

        // then
        assertThat(result.getUserId()).isEqualTo(USER_ID);
        assertThat(result.getNickname()).isEqualTo("머니몽");
        then(userRepository).should().save(any(User.class));
        then(appleUserRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("닉네임이 없는 OAuth 정보로 가입하면 기본 닉네임으로 저장한다.")
    void registerUserWithoutNickname() {
        // given
        givenUserSaveAssignsId();

        // when
        User saved = userService.registerUser(oAuthUserInfo(null, null));

        // then
        assertThat(saved.getNickname()).isEqualTo(UserService.DEFAULT_NICKNAME);
        assertThat(saved.getProvider()).isEqualTo(PROVIDER);
        assertThat(saved.getOauthId()).isEqualTo(OAUTH_ID);
        assertThat(saved.getEmail()).isEqualTo("user@example.com");
    }

    @Test
    @DisplayName("애플 리프레시 토큰이 있으면 애플 회원 정보를 함께 저장한다.")
    void registerAppleUser() {
        // given
        givenUserSaveAssignsId();

        // when
        userService.registerUser(oAuthUserInfo("애플유저", "apple-refresh-token-placeholder"));

        // then
        ArgumentCaptor<AppleUser> captor = ArgumentCaptor.forClass(AppleUser.class);
        then(appleUserRepository).should().save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().getAppleRefreshToken()).isEqualTo("apple-refresh-token-placeholder");
    }

    @Test
    @DisplayName("회원 프로필 조회 시 회원 정보와 대학 정보를 함께 반환한다.")
    void getUserProfile() {
        // given
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID, "머니몽")));
        given(userUniversityRepository.findByUserId(USER_ID))
                .willReturn(Optional.of(UserUniversity.of(USER_ID, "머니몽대학교", 2)));

        // when
        UserProfileResponse response = userService.getUserProfile(USER_ID);

        // then
        assertThat(response.getId()).isEqualTo(USER_ID);
        assertThat(response.getNickname()).isEqualTo("머니몽");
        assertThat(response.getProvider()).isEqualTo(PROVIDER);
        assertThat(response.getEmail()).isEqualTo("user@example.com");
        assertThat(response.getUniversityName()).isEqualTo("머니몽대학교");
        assertThat(response.getGrade()).isEqualTo(2);
    }

    @Test
    @DisplayName("대학 정보가 없는 회원의 프로필은 대학 정보를 비워서 반환한다.")
    void getUserProfileWithoutUniversity() {
        // given
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID, "머니몽")));
        given(userUniversityRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

        // when
        UserProfileResponse response = userService.getUserProfile(USER_ID);

        // then
        assertThat(response.getId()).isEqualTo(USER_ID);
        assertThat(response.getUniversityName()).isNull();
        assertThat(response.getGrade()).isNull();
    }

    @Test
    @DisplayName("존재하지 않는 회원의 프로필을 조회하면 예외가 발생한다.")
    void getUserProfileNotFound() {
        // given
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.getUserProfile(USER_ID))
                .isInstanceOf(NotFoundException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("회원 삭제 시 회원과 리프레시 토큰을 함께 삭제한다.")
    void delete() {
        // given
        User user = user(USER_ID, "머니몽");
        RefreshToken refreshToken = RefreshToken.builder().userId(USER_ID).token("token-placeholder").build();
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(refreshTokenRepository.findByUserId(USER_ID)).willReturn(Optional.of(refreshToken));

        // when
        userService.delete(USER_ID);

        // then
        then(userRepository).should().delete(user);
        then(refreshTokenRepository).should().delete(refreshToken);
    }

    @Test
    @DisplayName("리프레시 토큰이 없는 회원도 삭제할 수 있다.")
    void deleteWithoutRefreshToken() {
        // given
        User user = user(USER_ID, "머니몽");
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(refreshTokenRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

        // when
        userService.delete(USER_ID);

        // then
        then(userRepository).should().delete(user);
        then(refreshTokenRepository).should(never()).delete(any());
    }

    @Test
    @DisplayName("존재하지 않는 회원을 삭제하면 예외가 발생하고 토큰은 삭제하지 않는다.")
    void deleteNotFound() {
        // given
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.delete(USER_ID))
                .isInstanceOf(NotFoundException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
        then(userRepository).should(never()).delete(any());
        then(refreshTokenRepository).should(never()).findByUserId(any());
    }

    private void givenUserSaveAssignsId() {
        willAnswer(invocation -> {
            User unsaved = invocation.getArgument(0);
            ReflectionTestUtils.setField(unsaved, "id", USER_ID);
            return unsaved;
        }).given(userRepository).save(any(User.class));
    }

    private OAuthUserInfo oAuthUserInfo(String nickname, String appleRefreshToken) {
        return OAuthUserInfo.builder()
                .provider(PROVIDER)
                .oauthId(OAUTH_ID)
                .nickname(nickname)
                .email("user@example.com")
                .appleRefreshToken(appleRefreshToken)
                .build();
    }

    private User user(Long id, String nickname) {
        return User.builder()
                .id(id)
                .email("user@example.com")
                .nickname(nickname)
                .provider(PROVIDER)
                .oauthId(OAUTH_ID)
                .build();
    }
}
