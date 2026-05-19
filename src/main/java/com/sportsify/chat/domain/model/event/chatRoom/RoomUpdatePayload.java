package com.sportsify.chat.domain.model.event.chatRoom;

public record RoomUpdatePayload(
        String name,
        String imageUrl
) {
}
