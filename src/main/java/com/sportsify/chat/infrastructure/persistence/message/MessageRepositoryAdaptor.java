package com.sportsify.chat.infrastructure.persistence.message;

import com.sportsify.chat.domain.model.chatRoom.ChatRoomId;
import com.sportsify.chat.domain.model.message.Message;
import com.sportsify.chat.domain.model.message.MessageId;
import com.sportsify.chat.domain.repository.MessageRepo;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * MessageRepository(도메인 포트) 의 JPA 어댑터 구현체.
 */
@Repository
public class MessageRepositoryAdaptor implements MessageRepo {

    private final MessageJpaRepo jpaRepository;
    private final MessageMapper mapper;

    public MessageRepositoryAdaptor(MessageJpaRepo jpaRepository,
                                    MessageMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Message save(Message message) {
        MessageJpaEntity entity;
        if (message.getId() == null) {
            entity = mapper.toNewJpaEntity(message);
        } else {
            Long id = message.getId().value();
            entity = jpaRepository.findById(id)
                    .orElseThrow(() -> new IllegalStateException(
                            "Message not found for update: id=" + id));
            mapper.applyToJpa(entity, message);
        }
        MessageJpaEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Message> findById(MessageId id) {
        return jpaRepository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public List<Message> findByRoomBefore(ChatRoomId roomId, Long beforeMessageId, int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        PageRequest pageable = PageRequest.of(0, limit);
        List<MessageJpaEntity> rows;
        if (beforeMessageId == null) {
            rows = jpaRepository.findLatest(roomId.value(), pageable);
        } else {
            rows = jpaRepository.findBefore(roomId.value(), beforeMessageId, pageable);
        }
        return rows.stream().map(mapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public long countAfter(ChatRoomId roomId, Long afterMessageId) {
        if (afterMessageId == null) {
            return jpaRepository.countAll(roomId.value());
        }
        return jpaRepository.countAfter(roomId.value(), afterMessageId);
    }
}
