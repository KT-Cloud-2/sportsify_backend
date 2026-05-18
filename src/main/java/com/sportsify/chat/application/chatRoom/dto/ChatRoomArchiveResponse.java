package com.sportsify.chat.application.chatRoom.dto;

import com.sportsify.chat.domain.model.chatRoom.ChatRoom;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record ChatRoomArchiveResponse(
        @Schema(description = "채팅방 ID", example = "76") Long roomId,
        @Schema(description = "채팅방 상태", example = "ARCHIVED") String status,
        @Schema(description = "채팅방 업데이트일", example = "2026-04-27T14:22:15Z") LocalDateTime updatedAt
) {
    public static ChatRoomArchiveResponse from(ChatRoom room) {
        return new ChatRoomArchiveResponse(
                room.getId().value(),
                room.getStatus().name(),
                room.getUpdatedAt()
        );
    }
}
