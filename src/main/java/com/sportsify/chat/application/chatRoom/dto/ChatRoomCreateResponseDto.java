package com.sportsify.chat.application.chatRoom.dto;

import com.sportsify.chat.domain.model.chatRoom.ChatRoom;

import java.time.LocalDateTime;

public record ChatRoomCreateResponseDto(
        Long roomId,
        String type,
        Long gameId,
        String name,
        String imageUrl,
        Long createdBy,
        LocalDateTime createdAt
) {
    public static ChatRoomCreateResponseDto from(ChatRoom room) {
        return new ChatRoomCreateResponseDto(
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
