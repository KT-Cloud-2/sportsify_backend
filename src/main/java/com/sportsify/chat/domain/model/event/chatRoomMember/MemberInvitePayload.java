package com.sportsify.chat.domain.model.event.chatRoomMember;

public record MemberInvitePayload(
        Long inviterId,
        Long invitedId
) {
}
