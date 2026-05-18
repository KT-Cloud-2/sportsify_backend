package com.sportsify.chat.application.message.dto;

import com.sportsify.chat.domain.model.chatRoom.MemberId;
import com.sportsify.chat.domain.model.message.MessageId;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

public record MessageMemberInfoSummaryResponse(
        @Schema(description = "채팅방 멤버 ID", example = "12") Long memberId,
        @Schema(description = "채팅방 멈버의 마지막으로 읽은 message ID", example = "1233") Long lastReadMessageId
) {
    public static List<MessageMemberInfoSummaryResponse> of(Map<MemberId, MessageId> chatRoomMembersInfo) {
        return chatRoomMembersInfo != null ? chatRoomMembersInfo.entrySet()
                .stream()
                .map(entry -> new MessageMemberInfoSummaryResponse(
                        entry.getKey().value(),
                        entry.getValue().value()
                ))
                .toList() : null;
    }
}
