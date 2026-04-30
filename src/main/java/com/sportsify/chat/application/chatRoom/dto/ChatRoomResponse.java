package com.sportsify.chat.application.chatRoom.dto;

import com.sportsify.chat.domain.model.chatRoom.ChatRoom;

import java.time.LocalDateTime;

public record ChatRoomResponse(
        Long roomId,
        String type,
        Long gameId,
        String name,
        String imageUrl,
        Long createdBy,
        LocalDateTime createdAt
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
