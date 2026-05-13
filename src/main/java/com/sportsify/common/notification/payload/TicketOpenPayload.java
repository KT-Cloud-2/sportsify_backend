package com.sportsify.common.notification.payload;

import java.time.LocalDateTime;

public record TicketOpenPayload(
        Long gameId,
        String homeTeam,
        String awayTeam,
        LocalDateTime salesStartAt,
        LocalDateTime gameStartAt
) implements NotificationPayload {}
