package com.sportsify.chat.infrastructure.webSocket.dto;

import com.sportsify.chat.domain.model.event.EventEnvelope;
import com.sportsify.chat.domain.model.event.message.MessageSentPayload;

import java.util.List;

public record ReplayBatch(List<EventEnvelope<MessageSentPayload>> messages) {
}
