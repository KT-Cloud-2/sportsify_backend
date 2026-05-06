package com.sportsify.notification.presentation.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateNotificationSettingRequest(
        @NotNull Boolean ticketOpenAlert,
        @NotNull Boolean gameStartAlert,
        @NotNull Boolean paymentAlert
) {}
