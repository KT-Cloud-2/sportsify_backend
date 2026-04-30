package com.sportsify.chat.application.chatRoom.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatRoomGetRequest(
        @NotBlank String type,
        Long cursor,
        Integer limit
) {
    public ChatRoomGetRequest {
        limit = null != limit || limit < 1 ? Math.min(limit, 100) : 20;
    }
}
