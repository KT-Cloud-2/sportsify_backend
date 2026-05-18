package com.sportsify.chat.application.message.dto;

import com.sportsify.chat.domain.model.message.Message;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record MessageSummaryResponse(
        @Schema(description = "메시지 ID", example = "9981") Long messageId,
        @Schema(description = "채팅방 ID", example = "201") Long roomId,
        @Schema(description = "메시지 TYPE", example = "TEXT") String type,
        @Schema(description = "메시지 STATUS", example = "ACTIVE") String status,
        @Schema(description = "메시지 생성일", example = "2026-04-27T14:22:15Z") Instant createdAt
) {
    public static MessageSummaryResponse from(Message message) {
        return new MessageSummaryResponse(
                message.getId().value(),
                message.getRoomId().value(),
                message.getType().name(),
                message.getStatus().name(),
                message.getCreatedAt()
        );
    }
}
