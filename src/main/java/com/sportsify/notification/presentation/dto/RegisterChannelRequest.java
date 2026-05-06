package com.sportsify.notification.presentation.dto;

import com.sportsify.notification.domain.model.NotificationChannelType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RegisterChannelRequest(
        @NotNull NotificationChannelType channelType,
        @NotBlank String channelTarget
) {}
