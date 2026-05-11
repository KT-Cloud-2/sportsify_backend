package com.sportsify.notification.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationEventType {
    TICKET_OPEN("ticket.opened"),
    GAME_START("game.starting"),
    PAYMENT_COMPLETED("payment.completed"),
    CHAT_MENTION("chat.mentioned");

    private final String streamKey;
}
