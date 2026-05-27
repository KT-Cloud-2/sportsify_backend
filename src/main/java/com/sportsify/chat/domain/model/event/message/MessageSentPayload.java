package com.sportsify.chat.domain.model.event.message;

import com.sportsify.chat.domain.model.chatRoom.ChatRoomType;
import com.sportsify.chat.domain.model.message.Message;

public record MessageSentPayload(
        Long messageId,
        String clientMessageId,
        Long senderId,
        String type,
        String content,
        ChatRoomType roomType
) implements MessagePayload {

    public static MessageSentPayload from(Message message, String clientMessageId) {
        return new MessageSentPayload(
                message.getId() != null ? message.getId().value() : null,
                clientMessageId,
                message.getSenderId() != null ? message.getSenderId().value() : null,
                message.getType().name(),
                message.getContent().value(),
                null
        );
    }

    public MessageSentPayload withRoomType(ChatRoomType type) {
        return new MessageSentPayload(messageId, clientMessageId, senderId, this.type, content, type);
    }
}
