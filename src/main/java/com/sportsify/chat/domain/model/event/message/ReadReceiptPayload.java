package com.sportsify.chat.domain.model.event.message;

public record ReadReceiptPayload(
        Long memberId,
        Long lastReadMessageId
) implements MessagePayload {
}
