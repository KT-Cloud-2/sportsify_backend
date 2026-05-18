package com.sportsify.chat.presentation.message.dto;

public record ChatSendPayload(
        String clientMessageId,
        Long roomId,
        String type,
        String content
) {
}