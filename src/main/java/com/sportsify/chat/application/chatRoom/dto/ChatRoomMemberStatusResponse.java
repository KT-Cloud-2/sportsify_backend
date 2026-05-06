package com.sportsify.chat.application.chatRoom.dto;

import com.sportsify.chat.domain.model.chatRoomMember.ChatRoomMember;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record ChatRoomMemberStatusResponse(
        @Schema(description = "사용자 상태", example = "JOINED") String status,
        @Schema(description = "사용자 채팅방 알림 여부", example = "true") boolean notificationEnabled,
        @Schema(description = "마지막으로 읽은 MESSAGE ID", example = "13") Long lastReadMessageId,
        @Schema(description = "사용자 채팅방 참여일", example = "2026-04-27T14:22:15Z") LocalDateTime joinedAt
) {


    public static ChatRoomMemberStatusResponse from(ChatRoomMember member) {
        return new ChatRoomMemberStatusResponse(
                member.getStatus().name(),
                member.isNotificationEnabled(),
                member.getLastReadMessageId(),
                member.getJoinedAt()
        );
    }
}
