package com.sportsify.chat.infrastructure.persistence.message;


import com.sportsify.chat.domain.model.chatRoom.ChatRoomId;
import com.sportsify.chat.domain.model.chatRoom.MemberId;
import com.sportsify.chat.domain.model.message.*;
import org.springframework.stereotype.Component;

@Component
public class MessageMapper {
    /**
     *
     */
    public Message toDomain(MessageJpaEntity entity) {
        return Message.restore(
                MessageId.of(entity.getId()),
                ChatRoomId.of(entity.getRoomId()),
                entity.getSenderId() != null ? MemberId.of(entity.getSenderId()) : null,
                MessageContent.of(entity.getContent()),
                MessageType.valueOf(entity.getType()),
                MessageStatus.valueOf(entity.getStatus()),
                entity.getCreatedAt()
        );
    }

    /**
     * Message domain -> Message entity
     */
    public MessageJpaEntity toNewJpaEntity(Message domain) {
        return new MessageJpaEntity(
                null,
                domain.getRoomId().value(),
                domain.getSenderId() != null ? domain.getSenderId().value() : null,
                domain.getContent().value(),
                domain.getType().name(),
                domain.getStatus().name(),
                domain.getCreatedAt()
        );
    }

    /**
     * Message entity -> Message domain
     */
    public void applyToJpa(MessageJpaEntity entity, Message domain) {
        entity.setContent(domain.getContent().value());
        entity.setStatus(domain.getStatus().name());
        // roomId, senderId, type, createdAt 은 변경 불가
    }
}