package com.sportsify.chat.application.chatRoomMember.dto;

import com.sportsify.chat.domain.model.chatRoomMember.ChatRoomMember;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record ChatRoomMemberResponse(
        @Schema(description = "채팅방 ID", example = "16") Long roomId,
        @Schema(description = "채팅방 사용자 ID", example = "78") Long memberId,
        @Schema(description = "채팅방 사용자 상태", example = "JOINED") String status,
        @Schema(description = "채팅방 사용자 참가일", example = "2026-04-27T14:22:15Z") LocalDateTime joinedAt
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
