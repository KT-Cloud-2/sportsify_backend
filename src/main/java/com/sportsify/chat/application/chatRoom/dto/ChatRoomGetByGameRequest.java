package com.sportsify.chat.application.chatRoom.dto;

import jakarta.validation.constraints.Min;

public record ChatRoomGetByGameRequest(
        Long cursor,
        @Min(1) Integer limit
) {
    public ChatRoomGetByGameRequest {
        limit = null != limit ? Math.min(limit, 100) : 20;
    }
}