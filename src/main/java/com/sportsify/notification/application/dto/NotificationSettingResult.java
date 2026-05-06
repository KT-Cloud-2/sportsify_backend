package com.sportsify.notification.application.dto;

import com.sportsify.notification.domain.model.NotificationSetting;

public record NotificationSettingResult(
        boolean ticketOpenAlert,
        boolean gameStartAlert,
        boolean paymentAlert
) {
    public static NotificationSettingResult from(NotificationSetting setting) {
        return new NotificationSettingResult(
                setting.isTicketOpenAlert(),
                setting.isGameStartAlert(),
                setting.isPaymentAlert()
        );
    }
}
