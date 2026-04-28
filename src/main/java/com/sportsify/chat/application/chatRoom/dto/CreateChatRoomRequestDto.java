package com.sportsify.chat.application.chatRoom.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record CreateChatRoomRequestDto(
        @NotBlank String type,
        String name,
        String imageUrl,
        Long gameId,
        List<Long> inviteeIds
) {
}