package com.sportsify.chat.infrastructure.persistence.chatRoom;

import com.sportsify.chat.domain.model.chatRoom.*;
import org.springframework.stereotype.Component;

/**
 * 도메인 모델 ↔ JPA 영속 엔티티 변환기.
 */
@Component
public class ChatRoomMapper {


    public ChatRoom toDomain(ChatRoomJpaEntity entity) {

        return ChatRoom.restore(
                ChatRoomId.of(entity.getId()),
                ChatRoomName.of(entity.getName()),
                ChatRoomType.valueOf(entity.getType()),
                entity.getImageUrl(),
                GameId.of(entity.getGameId()),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                ChatRoomStatus.valueOf(entity.getStatus()),
                MemberId.of(entity.getCreatedBy())

        );
    }


    public ChatRoomJpaEntity toNewJpaEntity(ChatRoom domain) {
        return new ChatRoomJpaEntity(
                null,
                domain.getName().value(),
                domain.getType().name(),
                domain.getImageUrl(),
                domain.getGameId().value(),
                domain.getCreatedAt(),
                domain.getUpdatedAt(),
                domain.getStatus().name(),
                domain.getCreatedBy().value()
        );
    }


    public void applyToJpa(ChatRoomJpaEntity entity, ChatRoom domain) {
        entity.setName(domain.getName().value());
        entity.setType(domain.getType().name());
        entity.setImageUrl(domain.getImageUrl());
        entity.setGameId(domain.getGameId().value());
        entity.setUpdatedAt(domain.getUpdatedAt());
        entity.setStatus(domain.getStatus().name());
    }

}