package com.sportsify.notification.presentation.dto;

import com.sportsify.notification.application.dto.NotificationChannelResult;
import com.sportsify.notification.domain.model.NotificationChannelType;

public record NotificationChannelResponse(
        Long id,
        NotificationChannelType channelType,
        String channelTarget,
        boolean enabled
) {
    public static NotificationChannelResponse from(NotificationChannelResult result) {
        return new NotificationChannelResponse(
                result.id(),
                result.channelType(),
                result.channelTarget(),
                result.enabled()
        );
    }
}
