package com.sportsify.chat.application.chatRoom.dto;

import com.sportsify.chat.domain.model.chatRoom.ChatRoom;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.Optional;

public record ChatRoomDetailResponse(
        @Schema(description = "ID", example = "1") Long roomId,
        @Schema(description = "타입", example = "GAME") String type,
        @Schema(description = "채팅방 게임 id", example = "1") Long gameId,
        @Schema(description = "채팅방 이름", example = "한화 VS LG") String name,
        @Schema(description = "이미지 url", example = "https://image-040...") String imageUrl,
        @Schema(description = "참여자 수", example = "10") long currentParticipants,
        @Schema(description = "생성자", example = "3") Long createdBy,
        @Schema(description = "생성일", example = "2026-04-27T14:22:15Z") LocalDateTime createdAt,
        @Schema(description = "나의 채팅방 멤보 정보", example = "myMemberShip:[...]") Optional<ChatRoomMemberStatusResponse> myMembership
) {
    public static ChatRoomDetailResponse from(ChatRoom room, long count, ChatRoomMemberStatusResponse myMembership) {
        return new ChatRoomDetailResponse(
                room.getId().value(),
                room.getType().name(),
                room.getGameId() != null ? room.getGameId().value() : null,
                room.getName().value(),
                room.getImageUrl(),
                count,
                room.getCreatedBy().value(),
                room.getCreatedAt(),
                Optional.ofNullable(myMembership)
        );


    }
}
