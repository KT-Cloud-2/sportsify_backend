package com.sportsify.notification.application.dto;

public record UpdateNotificationSettingCommand(
        boolean ticketOpenAlert,
        boolean gameStartAlert,
        boolean paymentAlert,
        boolean chatMentionAlert
) {}
