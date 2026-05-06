package com.sportsify.chat.application.chatRoom.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;

public record ChatRoomGetByGameRequest(
        @Schema(description = "조회 시작 채팅방 ID", example = "12") Long cursor,
        @Schema(description = "채팅방 조회 제한", example = "20") @Min(1) Integer limit
) {
    public ChatRoomGetByGameRequest {
        limit = null != limit ? Math.min(limit, 100) : 20;
    }
}