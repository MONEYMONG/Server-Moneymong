package com.moneymong.domain.auth.service;

import com.moneymong.global.exception.enums.ErrorCode;
import com.moneymong.domain.auth.dto.AuthUserInfo;
import com.moneymong.domain.auth.service.dto.response.TokenResponse;
import com.moneymong.domain.auth.dto.Tokens;
import com.moneymong.domain.auth.dto.jwt.JwtAuthentication;
import com.moneymong.domain.auth.dto.jwt.JwtAuthenticationToken;
import com.moneymong.domain.auth.entity.RefreshToken;
import com.moneymong.domain.auth.exception.ExpiredTokenException;
import com.moneymong.domain.auth.exception.RefreshTokenNotFoundException;
import com.moneymong.domain.auth.repository.RefreshTokenRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    private static final long ONE_WEEK_IN_MILLI_SECONDS = 604800000L;

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.expiry-seconds.refresh-token}")
    private long refreshTokenExpireSeconds;

    public Tokens createTokens(AuthUserInfo authUserInfo) {
        Long userId = authUserInfo.getUserId();
        String role = authUserInfo.getRole();

        String accessToken = createAccessToken(userId, role);
        String refreshToken = createRefreshToken(userId, role);

        return new Tokens(accessToken, refreshToken);
    }

    private String createAccessToken(Long userId, String role) {
        return jwtTokenProvider.getAccessToken(userId, role);
    }

    private String createRefreshToken(Long userId, String role) {
        RefreshToken refreshToken = RefreshToken.builder()
                .token(UUID.randomUUID().toString())
                .userId(userId)
                .role(role)
                .expiredAt(refreshTokenExpiredAtFromNow())
                .timeToLive(refreshTokenExpireSeconds)
                .build();

        return refreshTokenRepository.save(refreshToken).getToken();
    }

    public TokenResponse getAccessTokensByRefreshToken(String refreshToken) {
        RefreshToken token = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new RefreshTokenNotFoundException(ErrorCode.REFRESH_TOKEN_NOT_FOUND));

        validateExpiration(token);

        if (token.getExpiredAt() < System.currentTimeMillis() + ONE_WEEK_IN_MILLI_SECONDS) {
            String renewalRefreshToken = UUID.randomUUID().toString();
            token.renew(renewalRefreshToken, refreshTokenExpiredAtFromNow(), refreshTokenExpireSeconds);

            refreshTokenRepository.save(token);
        }

        String accessToken = createAccessToken(token.getUserId(), token.getRole());
        return new TokenResponse(accessToken, token.getToken());
    }

    private long refreshTokenExpiredAtFromNow() {
        return System.currentTimeMillis() + refreshTokenExpireSeconds * 1000;
    }

    private void validateExpiration(RefreshToken token) {
        long expiredAt = token.getExpiredAt();
        long currentTime = System.currentTimeMillis();

        if (expiredAt < currentTime) {
            throw new ExpiredTokenException(ErrorCode.REFRESH_TOKEN_EXPIRED);
        }
    }

    public JwtAuthenticationToken getAuthenticationByAccessToken(String accessToken) {
        jwtTokenProvider.validateToken(accessToken);

        Claims claims = jwtTokenProvider.getClaims(accessToken);

        Long id = claims.get("userId", Long.class);
        String role = claims.get("role", String.class);

        JwtAuthentication principal = new JwtAuthentication(id, accessToken);
        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(role));

        return new JwtAuthenticationToken(principal, null, authorities);
    }

    public void deleteRefreshToken(String refreshToken) {
        refreshTokenRepository.findByToken(refreshToken)
                .ifPresent(refreshTokenRepository::delete);
    }

}
