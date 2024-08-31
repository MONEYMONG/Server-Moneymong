package com.moneymong.domain.user.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class LoginSuccessResponse {
    private String accessToken;
    private String refreshToken;
    private boolean loginSuccess;
    private boolean schoolInfoExist;
    private boolean schoolInfoProvided;

    public static LoginSuccessResponse of(
        String accessToken,
        String refreshToken,
        boolean loginSuccess,
        boolean schoolInfoExist,
        boolean schoolInfoProvided
    ) {
        return LoginSuccessResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .loginSuccess(loginSuccess)
            .schoolInfoExist(schoolInfoExist)
            .schoolInfoProvided(schoolInfoProvided)
            .build();
    }
}
