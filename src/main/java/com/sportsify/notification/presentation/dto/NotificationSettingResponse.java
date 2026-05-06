package com.sportsify.notification.presentation.dto;

import com.sportsify.notification.application.dto.NotificationSettingResult;

public record NotificationSettingResponse(
        boolean ticketOpenAlert,
        boolean gameStartAlert,
        boolean paymentAlert,
        boolean chatMentionAlert
) {
    public static NotificationSettingResponse from(NotificationSettingResult result) {
        return new NotificationSettingResponse(
                result.ticketOpenAlert(),
                result.gameStartAlert(),
                result.paymentAlert(),
                result.chatMentionAlert()
        );
    }
}
