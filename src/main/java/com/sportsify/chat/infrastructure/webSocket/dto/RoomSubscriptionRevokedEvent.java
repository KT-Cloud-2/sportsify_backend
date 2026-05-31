package com.sportsify.chat.infrastructure.webSocket.dto;

public record RoomSubscriptionRevokedEvent(String sessionId, Long roomId) {
}
