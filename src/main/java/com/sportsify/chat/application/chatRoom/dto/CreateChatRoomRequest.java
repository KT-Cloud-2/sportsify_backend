package com.sportsify.chat.application.chatRoom.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record CreateChatRoomRequest(
        @NotBlank String type,
        String name,
        String imageUrl,
        Long gameId,
        List<Long> inviteeIds
) {
    public CreateChatRoomRequest {
        inviteeIds = inviteeIds != null ? inviteeIds : List.of();
    }
}