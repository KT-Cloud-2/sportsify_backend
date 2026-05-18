package com.sportsify.common.notification.payload;

import java.time.LocalDateTime;

public record GameStartPayload(
        Long gameId,
        String homeTeam,
        String awayTeam,
        LocalDateTime gameStartAt
) implements NotificationPayload {}
