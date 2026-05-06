package com.sportsify.chat.application.chatRoomMember.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record ChatRoomMemberInviteRequest(
        @Schema(description = "채팅방 ID", example = "76") Long roomId,
        @Schema(description = "채팅방 초대 목록", example = "inviteeIds : [...]") List<Long> inviteeIds
) {
}
