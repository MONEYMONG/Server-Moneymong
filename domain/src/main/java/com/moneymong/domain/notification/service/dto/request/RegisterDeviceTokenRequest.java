package com.moneymong.domain.notification.service.dto.request;

import com.moneymong.domain.notification.entity.DeviceToken;
import com.moneymong.domain.notification.entity.enums.DevicePlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class RegisterDeviceTokenRequest {

    @NotBlank(message = "deviceId를 입력해주세요.")
    @Size(max = DeviceToken.DEVICE_ID_MAX_LENGTH, message = "deviceId는 64자 이하로 입력해주세요.")
    private String deviceId;

    @NotBlank(message = "token을 입력해주세요.")
    @Size(max = DeviceToken.TOKEN_MAX_LENGTH, message = "token은 512자 이하로 입력해주세요.")
    private String token;

    @NotNull(message = "platform을 입력해주세요.")
    private DevicePlatform platform;
}
