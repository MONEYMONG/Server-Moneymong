package com.moneymong.domain.user.api;

import com.moneymong.domain.user.service.dto.request.LoginRequest;
import com.moneymong.domain.user.service.dto.request.UpdateNotificationSettingRequest;
import com.moneymong.domain.user.service.dto.response.NotificationSettingResponse;
import com.moneymong.domain.user.service.dto.response.UserProfileResponse;
import com.moneymong.domain.user.service.UserFacadeService;
import com.moneymong.domain.user.service.UserService;
import com.moneymong.domain.user.service.dto.response.LoginSuccessResponse;
import com.moneymong.domain.auth.dto.jwt.JwtAuthentication;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "1. [유저]")
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@RestController
public class UserController {

    private final UserFacadeService userFacadeService;
    private final UserService userService;

    @Operation(summary = "로그인 및 가입")
    @PostMapping
    public LoginSuccessResponse login(
            @RequestBody @Valid LoginRequest loginRequest
    ) {
        return userFacadeService.login(loginRequest);
    }

    @Operation(summary = "내 정보 조회 API")
    @GetMapping("/me")
    public UserProfileResponse getMyProfile(
            @AuthenticationPrincipal JwtAuthentication user
    ) {
        return userService.getUserProfile(user.getId());
    }

    @Operation(summary = "회원 탈퇴")
    @DeleteMapping("/me")
    public void deleteUser(
            @AuthenticationPrincipal JwtAuthentication user
    ) {
        userFacadeService.delete(user.getId());
    }

    @Operation(summary = "내 알림 설정 조회")
    @GetMapping("/me/notification-settings")
    public NotificationSettingResponse getNotificationSetting(
            @AuthenticationPrincipal JwtAuthentication user
    ) {
        return userService.getNotificationSetting(user.getId());
    }

    @Operation(summary = "내 알림 설정 변경")
    @PatchMapping("/me/notification-settings")
    public NotificationSettingResponse updateNotificationSetting(
            @AuthenticationPrincipal JwtAuthentication user,
            @RequestBody @Valid UpdateNotificationSettingRequest request
    ) {
        return userService.updateNotificationSetting(user.getId(), request);
    }
}
