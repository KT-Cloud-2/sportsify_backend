package com.sportsify.chat.application.chatRoom.dto;

import com.sportsify.chat.domain.model.chatRoom.ChatRoom;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record ChatRoomResponse(
        @Schema(description = "채팅방 ID", example = "15") Long roomId,
        @Schema(description = "타입", example = "DIRECT") String type,
        @Schema(description = "채팅방 게임 ID", example = "null") Long gameId,
        @Schema(description = "채팅방 이름", example = "개인 채팅방") String name,
        @Schema(description = "채팅방 이미지 URL", example = "https://image-040...") String imageUrl,
        @Schema(description = "채팅방 생성자", example = "16") Long createdBy,
        @Schema(description = "채팅방 생성일", example = "2026-04-27T14:22:15Z") LocalDateTime createdAt
) {
    public static ChatRoomResponse from(ChatRoom room) {
        return new ChatRoomResponse(
                room.getId().value(),
                room.getType().name(),
                room.getGameId() != null ? room.getGameId().value() : null,
                room.getName().value(),
                room.getImageUrl(),
                room.getCreatedBy().value(),
                room.getCreatedAt()
        );
    }
}
