package com.sportsify.chat.application.chatRoom.dto;

import com.sportsify.chat.domain.model.chatRoom.ChatRoom;

public record ChatRoomUpdateRequest(
        String name,
        String imageUrl
) {
    public static ChatRoomUpdateRequest from(ChatRoom room) {
        return new ChatRoomUpdateRequest(
                room.getName().value(),
                room.getImageUrl()
        );
    }
}
