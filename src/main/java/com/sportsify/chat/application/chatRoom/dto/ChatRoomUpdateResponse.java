package com.sportsify.chat.application.chatRoom.dto;

import com.sportsify.chat.domain.model.chatRoom.ChatRoom;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record ChatRoomUpdateResponse(
        @Schema(description = "채팅방 ID", example = "76") Long roomId,
        @Schema(description = "채팅방 이름", example = "한화 VS LG") String name,
        @Schema(description = "채팅방 이미지 URL", example = "https://image-040...") String imageUrl,
        @Schema(description = "채팅방 업데이트일", example = "2026-04-27T14:22:15Z") LocalDateTime updatedAt
) {
    public static ChatRoomUpdateResponse from(ChatRoom room) {
        return new ChatRoomUpdateResponse(
                room.getId().value(),
                room.getName().value(),
                room.getImageUrl(),
                room.getUpdatedAt()
        );
    }
}
