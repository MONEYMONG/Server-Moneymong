package com.moneymong.domain.auth.api;

import com.moneymong.domain.auth.api.request.DeleteRefreshTokenRequest;
import com.moneymong.domain.auth.api.request.RefreshAccessTokenRequest;
import com.moneymong.domain.auth.service.dto.response.TokenResponse;
import com.moneymong.domain.auth.service.TokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "0. [로그인]")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/tokens")
public class TokenController {

	private final TokenService tokenService;

	@Operation(summary = "AccessToken 갱신")
	@PostMapping
	public TokenResponse refreshAccessToken(@RequestBody RefreshAccessTokenRequest refreshAccessTokenRequest) {
		String refreshToken = refreshAccessTokenRequest.getRefreshToken();
		return tokenService.getAccessTokensByRefreshToken(refreshToken);
	}

	@Operation(summary = "로그아웃")
	@DeleteMapping
	public void deleteRefreshToken(@RequestBody DeleteRefreshTokenRequest deleteRefreshTokenRequest) {
		String refreshToken = deleteRefreshTokenRequest.getRefreshToken();
		tokenService.deleteRefreshToken(refreshToken);
	}
}
