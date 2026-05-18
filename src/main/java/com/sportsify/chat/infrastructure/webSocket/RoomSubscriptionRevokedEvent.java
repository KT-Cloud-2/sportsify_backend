package com.sportsify.chat.infrastructure.webSocket;

public record RoomSubscriptionRevokedEvent(String sessionId, Long roomId) {}
