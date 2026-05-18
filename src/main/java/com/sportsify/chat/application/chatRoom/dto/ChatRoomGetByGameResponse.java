package com.sportsify.chat.application.chatRoom.dto;

import com.sportsify.chat.domain.model.chatRoom.ChatRoom;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record ChatRoomGetByGameResponse(
        @Schema(description = "유효한 리프레시 토큰", example = "12") Long roomId,
        @Schema(description = "유효한 리프레시 토큰", example = "GAME") String type,
        @Schema(description = "유효한 리프레시 토큰", example = "13") Long gameId,
        @Schema(description = "유효한 리프레시 토큰", example = "한화 VS LG") String name,
        @Schema(description = "채팅방 이미지 URL", example = "https://image-040...") String imageUrl,
        @Schema(description = "채팅방 참여자 수", example = "20") long currentParticipants,
        @Schema(description = "유효한 리프레시 토큰", example = "2026-04-27T14:22:15Z") LocalDateTime createdAt
) {
    public static ChatRoomGetByGameResponse from(ChatRoom room, long participantCount) {
        return new ChatRoomGetByGameResponse(
                room.getId().value(),
                room.getType().name(),
                room.getGameId() != null ? room.getGameId().value() : null,
                room.getName().value(),
                room.getImageUrl(),
                participantCount,
                room.getCreatedAt()
        );
    }

}
