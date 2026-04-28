package com.sportsify.chat.application.chatRoom;

import com.sportsify.chat.domain.model.chatRoom.ChatRoom;
import com.sportsify.chat.domain.model.chatRoomMember.ChatRoomMember;

import java.time.LocalDateTime;

public record ChatRoomSummaryResponse(
        Long roomId,
        String type,
        Long gameId,
        String title,
        String imageUrl,
        long currentParticipants,
        Boolean notificationEnabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ChatRoomSummaryResponse of(ChatRoom room, long currentParticipants, ChatRoomMember membership) {
        return new ChatRoomSummaryResponse(
                room.getId().value(),
                room.getType().name(),
                room.getGameId() != null ? room.getGameId().value() : null,
                room.getName().value(),
                room.getImageUrl(),
                currentParticipants,
                membership != null ? membership.isNotificationEnabled() : null,
                room.getCreatedAt(),
                room.getUpdatedAt()
        );
    }
}
