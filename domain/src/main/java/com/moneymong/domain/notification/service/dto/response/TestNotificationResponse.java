package com.moneymong.domain.notification.service.dto.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TestNotificationResponse {

    private List<NotificationDeliveryResponse> deliveries;
}
