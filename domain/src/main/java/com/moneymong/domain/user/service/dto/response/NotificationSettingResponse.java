package com.moneymong.domain.user.service.dto.response;

import com.moneymong.domain.user.entity.User;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class NotificationSettingResponse {

    private boolean pushEnabled;

    public static NotificationSettingResponse from(User user) {
        return new NotificationSettingResponse(user.isPushEnabled());
    }
}
