package com.sportsify.chat.application.chatRoom.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record ChatRoomGetRequest(
        @Schema(description = "타입", example = "GAME") @NotBlank String type,
        @Schema(description = "조회 시작 채팅방 ID", example = "13") Long cursor,
        @Schema(description = "채팅방 조회 제한 수", example = "20") Integer limit
) {
    public ChatRoomGetRequest {
        limit = (limit == null || limit < 1) ? 20 : Math.min(limit, 100);
    }
}
