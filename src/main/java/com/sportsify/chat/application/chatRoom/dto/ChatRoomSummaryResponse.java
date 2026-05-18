package com.sportsify.chat.application.chatRoom.dto;

import com.sportsify.chat.domain.model.chatRoom.ChatRoom;
import com.sportsify.chat.domain.model.chatRoomMember.ChatRoomMember;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record ChatRoomSummaryResponse(
        @Schema(description = "채팅방 ID", example = "13") Long roomId,
        @Schema(description = "타입", example = "GAME") String type,
        @Schema(description = "채팅방 게임 ID", example = "13") Long gameId,
        @Schema(description = "채팅방 이름", example = "한화 VS LG") String name,
        @Schema(description = "채팅방 이미지 URL", example = "https://image-040...") String imageUrl,
        @Schema(description = "채팅방 참여자 수", example = "200") long currentParticipants,
        @Schema(description = "마지막으로 쓴 MESSAGE 정보", example = "lastMessage : {...}") ChatMessageResponse lastMessage,
        @Schema(description = "읽지 않는 MESSAGE 수", example = "200") Long unRead,
        @Schema(description = "사용자 채팅방 일림 여부", example = "False") Boolean notificationEnabled,
        @Schema(description = "생성일", example = "2026-04-27T14:22:15Z") LocalDateTime createdAt,
        @Schema(description = "업데이트일", example = "2026-04-27T14:22:15Z") LocalDateTime updatedAt
) {
    public static ChatRoomSummaryResponse of(ChatRoom room, long currentParticipants, ChatRoomMember membership, ChatMessageResponse message, Long unReadCount) {
        return new ChatRoomSummaryResponse(
                room.getId().value(),
                room.getType().name(),
                room.getGameId() != null ? room.getGameId().value() : null,
                room.getName().value(),
                room.getImageUrl(),
                currentParticipants,
                message,
                unReadCount,
                membership != null ? membership.isNotificationEnabled() : null,
                room.getCreatedAt(),
                room.getUpdatedAt()
        );
    }
}
