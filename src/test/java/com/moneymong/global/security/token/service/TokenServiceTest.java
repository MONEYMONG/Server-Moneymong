package com.moneymong.global.security.token.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import com.moneymong.global.exception.enums.ErrorCode;
import com.moneymong.global.security.oauth.dto.AuthUserInfo;
import com.moneymong.global.security.token.api.response.TokenResponse;
import com.moneymong.global.security.token.dto.Tokens;
import com.moneymong.global.security.token.dto.jwt.JwtAuthentication;
import com.moneymong.global.security.token.dto.jwt.JwtAuthenticationToken;
import com.moneymong.global.security.token.entity.RefreshToken;
import com.moneymong.global.security.token.exception.ExpiredTokenException;
import com.moneymong.global.security.token.exception.InvalidTokenException;
import com.moneymong.global.security.token.exception.RefreshTokenNotFoundException;
import com.moneymong.global.security.token.repository.RefreshTokenRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    private static final long REFRESH_TOKEN_EXPIRE_SECONDS = 1_209_600L; // 14일
    private static final long ONE_DAY_MILLIS = 86_400_000L;
    private static final Long USER_ID = 1L;
    private static final String ROLE = "USER";

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private TokenService tokenService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(tokenService, "refreshTokenExpireSeconds", REFRESH_TOKEN_EXPIRE_SECONDS);
    }

    private RefreshToken refreshToken(String token, long expiredAt) {
        return RefreshToken.builder()
                .token(token)
                .userId(USER_ID)
                .role(ROLE)
                .expiredAt(expiredAt)
                .build();
    }

    @Test
    @DisplayName("인증 유저 정보로 액세스 토큰과 리프레시 토큰을 발급하고 리프레시 토큰을 저장한다.")
    void createTokens() {
        given(jwtTokenProvider.getAccessToken(USER_ID, ROLE)).willReturn("access-token");
        given(refreshTokenRepository.save(any(RefreshToken.class))).willAnswer(invocation -> invocation.getArgument(0));
        long before = System.currentTimeMillis();

        Tokens tokens = tokenService.createTokens(AuthUserInfo.from(USER_ID, "몽몽", ROLE));

        assertThat(tokens.getAccessToken()).isEqualTo("access-token");
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        then(refreshTokenRepository).should().save(captor.capture());
        RefreshToken saved = captor.getValue();
        assertThat(tokens.getRefreshToken()).isEqualTo(saved.getToken()).isNotBlank();
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getRole()).isEqualTo(ROLE);
        assertThat(saved.getExpiredAt())
                .isBetween(before + REFRESH_TOKEN_EXPIRE_SECONDS * 1000, System.currentTimeMillis() + REFRESH_TOKEN_EXPIRE_SECONDS * 1000);
    }

    @Test
    @DisplayName("만료까지 일주일 넘게 남은 리프레시 토큰이면 액세스 토큰만 재발급하고 리프레시 토큰은 유지한다.")
    void refreshWithoutRenewal() {
        RefreshToken token = refreshToken("refresh-token", System.currentTimeMillis() + 10 * ONE_DAY_MILLIS);
        given(refreshTokenRepository.findByToken("refresh-token")).willReturn(Optional.of(token));
        given(jwtTokenProvider.getAccessToken(USER_ID, ROLE)).willReturn("new-access");

        TokenResponse response = tokenService.getAccessTokensByRefreshToken("refresh-token");

        assertThat(response.getAccessToken()).isEqualTo("new-access");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        then(refreshTokenRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("만료까지 일주일이 남지 않은 리프레시 토큰이면 새 리프레시 토큰으로 갱신해 저장한다.")
    void refreshWithRenewal() {
        RefreshToken token = refreshToken("old-refresh", System.currentTimeMillis() + ONE_DAY_MILLIS);
        given(refreshTokenRepository.findByToken("old-refresh")).willReturn(Optional.of(token));
        given(jwtTokenProvider.getAccessToken(USER_ID, ROLE)).willReturn("new-access");

        TokenResponse response = tokenService.getAccessTokensByRefreshToken("old-refresh");

        assertThat(response.getAccessToken()).isEqualTo("new-access");
        assertThat(response.getRefreshToken()).isNotEqualTo("old-refresh").isEqualTo(token.getToken());
        then(refreshTokenRepository).should().save(token);
    }

    @Test
    @DisplayName("저장되지 않은 리프레시 토큰이면 RefreshTokenNotFoundException이 발생한다.")
    void refreshTokenNotFound() {
        given(refreshTokenRepository.findByToken("unknown")).willReturn(Optional.empty());

        assertThatThrownBy(() -> tokenService.getAccessTokensByRefreshToken("unknown"))
                .isInstanceOf(RefreshTokenNotFoundException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.REFRESH_TOKEN_NOT_FOUND);

        then(jwtTokenProvider).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("만료된 리프레시 토큰이면 ExpiredTokenException이 발생한다.")
    void refreshTokenExpired() {
        RefreshToken token = refreshToken("expired", System.currentTimeMillis() - 1000);
        given(refreshTokenRepository.findByToken("expired")).willReturn(Optional.of(token));

        assertThatThrownBy(() -> tokenService.getAccessTokensByRefreshToken("expired"))
                .isInstanceOf(ExpiredTokenException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.REFRESH_TOKEN_EXPIRED);

        then(jwtTokenProvider).shouldHaveNoInteractions();
        then(refreshTokenRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("유효한 액세스 토큰이면 클레임의 userId와 role로 인증 객체를 만든다.")
    void getAuthenticationByAccessToken() {
        Claims claims = Jwts.claims(Map.of("userId", USER_ID, "role", ROLE));
        given(jwtTokenProvider.getClaims("access-token")).willReturn(claims);

        JwtAuthenticationToken authentication = tokenService.getAuthenticationByAccessToken("access-token");

        then(jwtTokenProvider).should().validateToken("access-token");
        JwtAuthentication principal = (JwtAuthentication) authentication.getPrincipal();
        assertThat(principal.getId()).isEqualTo(USER_ID);
        assertThat(principal.getAccessToken()).isEqualTo("access-token");
        assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly(ROLE);
    }

    @Test
    @DisplayName("유효하지 않은 액세스 토큰이면 InvalidTokenException이 발생하고 클레임을 조회하지 않는다.")
    void getAuthenticationByInvalidAccessToken() {
        willThrow(new InvalidTokenException(ErrorCode.INVALID_TOKEN)).given(jwtTokenProvider).validateToken("invalid");

        assertThatThrownBy(() -> tokenService.getAuthenticationByAccessToken("invalid"))
                .isInstanceOf(InvalidTokenException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_TOKEN);

        then(jwtTokenProvider).should(never()).getClaims(anyString());
    }

    @Test
    @DisplayName("만료된 액세스 토큰이면 ExpiredTokenException이 발생한다.")
    void getAuthenticationByExpiredAccessToken() {
        willThrow(new ExpiredTokenException(ErrorCode.TOKEN_EXPIRED)).given(jwtTokenProvider).validateToken("expired");

        assertThatThrownBy(() -> tokenService.getAuthenticationByAccessToken("expired"))
                .isInstanceOf(ExpiredTokenException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TOKEN_EXPIRED);

        then(jwtTokenProvider).should(never()).getClaims(anyString());
    }

    @Test
    @DisplayName("저장된 리프레시 토큰을 삭제한다.")
    void deleteRefreshToken() {
        RefreshToken token = refreshToken("refresh-token", System.currentTimeMillis() + ONE_DAY_MILLIS);
        given(refreshTokenRepository.findByToken("refresh-token")).willReturn(Optional.of(token));

        tokenService.deleteRefreshToken("refresh-token");

        then(refreshTokenRepository).should().delete(token);
    }

    @Test
    @DisplayName("저장되지 않은 리프레시 토큰이면 삭제하지 않고 넘어간다.")
    void deleteUnknownRefreshToken() {
        given(refreshTokenRepository.findByToken("unknown")).willReturn(Optional.empty());

        tokenService.deleteRefreshToken("unknown");

        then(refreshTokenRepository).should(never()).delete(any());
        then(jwtTokenProvider).should(never()).getAccessToken(anyLong(), anyString());
    }
}
