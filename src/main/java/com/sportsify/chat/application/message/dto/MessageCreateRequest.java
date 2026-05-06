package com.sportsify.chat.application.message.dto;

public record MessageCreateRequest(
        Long clientMessageId,
        Long roomId,
        String type,
        String content
) {
}
