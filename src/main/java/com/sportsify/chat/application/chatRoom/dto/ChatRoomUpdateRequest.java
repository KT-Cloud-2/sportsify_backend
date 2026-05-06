package com.sportsify.chat.application.chatRoom.dto;

import com.sportsify.chat.domain.model.chatRoom.ChatRoom;
import io.swagger.v3.oas.annotations.media.Schema;

public record ChatRoomUpdateRequest(
        @Schema(description = "채팅방 이름", example = "한화 VS LG") String name,
        @Schema(description = "채팅방 이미지 URL", example = "https://image-040...") String imageUrl
) {
    public static ChatRoomUpdateRequest from(ChatRoom room) {
        return new ChatRoomUpdateRequest(
                room.getName().value(),
                room.getImageUrl()
        );
    }
}
