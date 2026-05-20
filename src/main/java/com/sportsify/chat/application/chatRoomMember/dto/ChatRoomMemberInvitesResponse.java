package com.sportsify.chat.application.chatRoomMember.dto;

import com.sportsify.chat.domain.model.chatRoomMember.ChatRoomMember;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

public record ChatRoomMemberInvitesResponse(
        @Schema(description = "초대 목록", example = "invites: []") List<ChatRoomMemberInviteSummary> invites
) {
    public static ChatRoomMemberInvitesResponse from(List<ChatRoomMember> invites) {
        return new ChatRoomMemberInvitesResponse(
                invites.stream().map(invite ->
                        new ChatRoomMemberInviteSummary(
                                invite.getRoomId().value(),
                                invite.getStatus().name(),
                                invite.getUpdatedAt()
                        )
                ).toList()
        );
    }

    private record ChatRoomMemberInviteSummary(
            @Schema(description = "채팅방 ID", example = "16") Long roomId,
            @Schema(description = "채팅방 사용자 상태", example = "JOINED") String status,
            @Schema(description = "채팅방 사용자 초대일", example = "2026-04-27T14:22:15Z") LocalDateTime updatedAt
    ) {

    }
}


