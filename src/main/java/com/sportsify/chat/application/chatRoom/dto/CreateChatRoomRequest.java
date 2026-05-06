package com.sportsify.chat.application.chatRoom.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record CreateChatRoomRequest(
        @Schema(description = "채팅방 타입", example = "GAME") @NotBlank String type,
        @Schema(description = "채팅방 이름", example = "한화 VS LG") String name,
        @Schema(description = "채팅방 이미지 URL", example = "https://image-040...") String imageUrl,
        @Schema(description = "채팅방 게임 ID", example = "13") Long gameId,
        @Schema(description = "채팅방 초대 목록", example = "inviteeIds : [...]") List<Long> inviteeIds
) {
    public CreateChatRoomRequest {
        inviteeIds = inviteeIds != null ? inviteeIds : List.of();
    }
}
