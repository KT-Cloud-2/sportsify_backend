package com.sportsify.chat.application.chatRoom.dto;

import com.sportsify.chat.domain.model.chatRoomMember.ChatRoomMember;

import java.time.LocalDateTime;
import java.util.Optional;

public record ChatRoomMemberStatusResponse(
        String status,
        boolean notificationEnabled,
        Long lastReadMessageId,
        LocalDateTime joinedAt
) {


    public static ChatRoomMemberStatusResponse from(Optional<ChatRoomMember> member) {
        return member.map(m -> new ChatRoomMemberStatusResponse(
                m.getStatus().name(),
                m.isNotificationEnabled(),
                m.getLastReadMessageId(),
                m.getJoinedAt()
        )).orElse(null);
    }
}
