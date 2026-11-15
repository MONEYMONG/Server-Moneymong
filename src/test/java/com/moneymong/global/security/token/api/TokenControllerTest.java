package com.moneymong.global.security.token.api;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moneymong.global.exception.enums.ErrorCode;
import com.moneymong.global.security.token.api.request.DeleteRefreshTokenRequest;
import com.moneymong.global.security.token.api.request.RefreshAccessTokenRequest;
import com.moneymong.global.security.token.service.dto.response.TokenResponse;
import com.moneymong.global.security.token.exception.ExpiredTokenException;
import com.moneymong.global.security.token.exception.RefreshTokenNotFoundException;
import com.moneymong.global.security.token.service.TokenService;
import com.moneymong.support.ControllerTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.http.MediaType;

class TokenControllerTest extends ControllerTestSupport {

    @Mock
    private TokenService tokenService;

    @Override
    protected Object controller() {
        return new TokenController(tokenService);
    }

    @Test
    @DisplayName("리프레시 토큰으로 액세스 토큰을 재발급한다.")
    void refreshAccessToken() throws Exception {
        given(tokenService.getAccessTokensByRefreshToken("refresh-token"))
                .willReturn(new TokenResponse("new-access", "refresh-token"));

        mockMvc.perform(post("/api/v1/tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new RefreshAccessTokenRequest("refresh-token"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"));

        then(tokenService).should().getAccessTokensByRefreshToken("refresh-token");
    }

    @Test
    @DisplayName("저장되지 않은 리프레시 토큰으로 재발급하면 404를 반환한다.")
    void refreshAccessTokenNotFound() throws Exception {
        given(tokenService.getAccessTokensByRefreshToken("unknown"))
                .willThrow(new RefreshTokenNotFoundException(ErrorCode.REFRESH_TOKEN_NOT_FOUND));

        mockMvc.perform(post("/api/v1/tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new RefreshAccessTokenRequest("unknown"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TOKEN-003"));
    }

    @Test
    @DisplayName("만료된 리프레시 토큰으로 재발급하면 401을 반환한다.")
    void refreshAccessTokenExpired() throws Exception {
        given(tokenService.getAccessTokensByRefreshToken("expired"))
                .willThrow(new ExpiredTokenException(ErrorCode.REFRESH_TOKEN_EXPIRED));

        mockMvc.perform(post("/api/v1/tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new RefreshAccessTokenRequest("expired"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN-004"));
    }

    @Test
    @DisplayName("리프레시 토큰을 삭제한다.")
    void deleteRefreshToken() throws Exception {
        mockMvc.perform(delete("/api/v1/tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new DeleteRefreshTokenRequest("refresh-token"))))
                .andExpect(status().isOk());

        then(tokenService).should().deleteRefreshToken("refresh-token");
    }
}
