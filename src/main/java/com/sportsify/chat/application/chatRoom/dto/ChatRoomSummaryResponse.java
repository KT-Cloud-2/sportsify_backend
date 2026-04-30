package com.sportsify.chat.application.chatRoom.dto;

import com.sportsify.chat.domain.model.chatRoom.ChatRoom;
import com.sportsify.chat.domain.model.chatRoomMember.ChatRoomMember;

import java.time.LocalDateTime;

public record ChatRoomSummaryResponse(
        Long roomId,
        String type,
        Long gameId,
        String name,
        String imageUrl,
        long currentParticipants,
        Record lastMessage,
        Long unreadCount,
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
                null,
                null,
                membership != null ? membership.isNotificationEnabled() : null,
                room.getCreatedAt(),
                room.getUpdatedAt()
        );
    }
}
