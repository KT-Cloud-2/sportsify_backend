package com.sportsify.chat.presentation.message.dto;

public record ChatReadPayload(
        Long roomId,
        Long lastReadMessageId
) {
}

