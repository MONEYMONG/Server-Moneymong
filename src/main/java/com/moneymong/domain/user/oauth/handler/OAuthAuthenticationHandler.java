package com.moneymong.domain.user.oauth.handler;

import com.moneymong.domain.user.oauth.dto.OAuthUserDataRequest;
import com.moneymong.domain.user.oauth.dto.OAuthUserDataResponse;
import com.moneymong.domain.user.service.oauth.OAuthProvider;

public interface OAuthAuthenticationHandler {
    OAuthProvider getAuthProvider();

    OAuthUserDataResponse getOAuthUserData(OAuthUserDataRequest request);

    void unlink(Long userId);
}
