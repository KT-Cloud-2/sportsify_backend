package com.sportsify.chat.application.chatRoom;

import com.sportsify.chat.domain.model.chatRoom.ChatRoom;

import java.time.LocalDateTime;

public record ChatRoomUpdateResponse(
        Long roomId,
        String title,
        String imageUrl,
        LocalDateTime updatedAt
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
