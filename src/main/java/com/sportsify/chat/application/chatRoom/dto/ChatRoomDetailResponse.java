package com.sportsify.chat.application.chatRoom.dto;

import com.sportsify.chat.domain.model.chatRoom.ChatRoom;

import java.time.LocalDateTime;
import java.util.Optional;

public record ChatRoomDetailResponse(
        Long roomId,
        String type,
        Long gameId,
        String name,
        String imageUrl,
        long currentParticipants,
        Long createdBy,
        LocalDateTime createdAt,
        Optional<ChatRoomMemberStatusResponse> myMembership
) {
    public static ChatRoomDetailResponse from(ChatRoom room, long count, ChatRoomMemberStatusResponse myMembership) {
        return new ChatRoomDetailResponse(
                room.getId().value(),
                room.getType().name(),
                room.getGameId() != null ? room.getGameId().value() : null,
                room.getName().value(),
                room.getImageUrl(),
                count,
                room.getCreatedBy().value(),
                room.getCreatedAt(),
                Optional.ofNullable(myMembership)
        );


    }
}
