package com.sportsify.chat.application.chatRoomMember.dto;

import java.util.List;

public record ChatRoomMemberInviteRequest(
        Long roomId,
        List<Long> inviteeIds
) {
}
