package com.sportsify.common.notification;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
@RequiredArgsConstructor
public enum NotificationEventType {
    TICKET_OPEN("ticket.opened", true, false),
    GAME_START("game.starting", true, false),
    PAYMENT_COMPLETED("payment.completed", false, true),
    CHAT_MENTION("chat.mentioned", false, true);

    private final String streamKey;
    private final boolean scheduled;
    private final boolean singleTarget;

    public static Map<String, NotificationEventType> streamKeyMap() {
        return Arrays.stream(values())
                .collect(Collectors.toMap(NotificationEventType::getStreamKey, e -> e));
    }
}
