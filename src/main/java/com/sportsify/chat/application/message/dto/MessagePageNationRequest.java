package com.sportsify.chat.application.message.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record MessagePageNationRequest(
        @Schema(description = "조회 시작 메시지 ID", example = "13") Long cursor,
        @Schema(description = "메시지 조회 제한 수", example = "20") Integer limit
) {
    public MessagePageNationRequest {
        limit = (limit == null || limit < 1) ? 20 : Math.min(limit, 100);
    }
}
