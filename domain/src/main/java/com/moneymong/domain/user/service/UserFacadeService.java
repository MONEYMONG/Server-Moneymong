package com.moneymong.domain.user.service;

import com.moneymong.domain.agency.service.AgencyUserService;
import com.moneymong.domain.user.service.dto.request.LoginRequest;
import com.moneymong.domain.auth.dto.AuthUserInfo;
import com.moneymong.domain.user.service.dto.response.LoginSuccessResponse;
import com.moneymong.domain.user.oauth.dto.OAuthUserDataResponse;
import com.moneymong.domain.user.oauth.dto.OAuthUserInfo;
import com.moneymong.domain.user.service.oauth.OAuthService;
import com.moneymong.domain.auth.dto.Tokens;
import com.moneymong.domain.auth.service.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserFacadeService {
    private final UserService userService;
    private final TokenService tokenService;
    private final OAuthService oAuthService;
    private final UserUniversityService userUniversityService;
    private final AgencyUserService agencyUserService;

    public LoginSuccessResponse login(LoginRequest loginRequest) {
        OAuthUserDataResponse oAuthUserData = oAuthService.login(loginRequest);
        OAuthUserInfo oAuthUserInfo = OAuthUserInfo.from(oAuthUserData);
        AuthUserInfo registerResult = userService.getOrRegister(oAuthUserInfo);

        Tokens tokens = tokenService.createTokens(registerResult);

        boolean loginSuccess = true;
        boolean schoolInfoExists = userUniversityService.exists(registerResult.getUserId());

        return LoginSuccessResponse.of(
            tokens.getAccessToken(),
            tokens.getRefreshToken(),
            loginSuccess,
            schoolInfoExists,
            schoolInfoExists   // schoolInfoExists와 동일한 값을 반환하며, 추후 schoolInfoExists 필드를 삭제한다.
        );
    }

    @Transactional
    public void delete(Long userId) {
        oAuthService.revoke(userId);
        userService.delete(userId);
        userUniversityService.delete(userId);
        agencyUserService.deleteAll(userId);
    }
}
