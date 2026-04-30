package com.sportsify.chat.application.chatRoomMember.dto;

import com.sportsify.chat.domain.model.chatRoomMember.ChatRoomMember;

import java.time.LocalDateTime;

public record ChatRoomMemberResponse(
        Long roomId,
        Long memberId,
        String status,
        LocalDateTime joinedAt
) {
    public static ChatRoomMemberResponse from(ChatRoomMember member) {
        return new ChatRoomMemberResponse(
                member.getRoomId().value(),
                member.getMemberId().value(),
                member.getStatus().name(),
                member.getJoinedAt()
        );
    }
}
