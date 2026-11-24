package com.moneymong.domain.user.service.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateNotificationSettingRequest {

    @NotNull(message = "pushEnabled를 입력해주세요.")
    private Boolean pushEnabled;
}
