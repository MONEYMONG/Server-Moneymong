package com.moneymong.domain.user.oauth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class OAuthUserDataRequest {
    private String accessToken;
    private String code; // Apple Authorization Code
    private String name;
}
