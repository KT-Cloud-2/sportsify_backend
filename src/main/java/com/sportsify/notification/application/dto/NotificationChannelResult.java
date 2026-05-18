package com.sportsify.notification.application.dto;

import com.sportsify.notification.domain.model.NotificationChannel;
import com.sportsify.notification.domain.model.NotificationChannelType;

public record NotificationChannelResult(
        Long id,
        NotificationChannelType channelType,
        String channelTarget,
        boolean enabled
) {
    public static NotificationChannelResult from(NotificationChannel channel) {
        return new NotificationChannelResult(
                channel.getId(),
                channel.getChannelType(),
                channel.getChannelTarget(),
                channel.isEnabled()
        );
    }
}
