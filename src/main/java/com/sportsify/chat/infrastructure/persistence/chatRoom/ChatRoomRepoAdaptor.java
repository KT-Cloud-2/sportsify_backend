package com.sportsify.chat.infrastructure.persistence.chatRoom;

import com.sportsify.chat.domain.model.chatRoom.ChatRoom;
import com.sportsify.chat.domain.model.chatRoom.ChatRoomId;
import com.sportsify.chat.domain.repository.ChatRoomRepo;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * ChatRoomRepo(도메인 포트) 의 JPA 어댑터 구현체.
 */
@Repository
public class ChatRoomRepoAdaptor implements ChatRoomRepo {

    private final ChatRoomJpaRepo jpaRepository;
    private final ChatRoomMapper mapper;

    public ChatRoomRepoAdaptor(ChatRoomJpaRepo jpaRepository,
                               ChatRoomMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public ChatRoom save(ChatRoom chatRoom) {
        ChatRoomJpaEntity entity;
        if (chatRoom.getId() == null) {
            entity = mapper.toNewJpaEntity(chatRoom);
        } else {
            Long id = chatRoom.getId().value();
            entity = jpaRepository.findById(id)
                    .orElseThrow(() -> new IllegalStateException(
                            "ChatRoom not found for update: id=" + id));
            mapper.applyToJpa(entity, chatRoom);
        }
        ChatRoomJpaEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<ChatRoom> findById(ChatRoomId id) {
        return jpaRepository.findById(id.value()).map(mapper::toDomain);
    }


    @Override
    public Optional<ChatRoom> findByGameId(Long gameId) {
        if (gameId == null) return Optional.empty();
        return jpaRepository.findByGameId(gameId).map(mapper::toDomain);
    }

    @Override
    public boolean existsById(ChatRoomId id) {
        return jpaRepository.existsById(id.value());
    }

    @Override
    public boolean existByCreatorIdAndInviteId(Long creatorId, Long inviteId) {
        return jpaRepository.exists()
    }

    @Override
    public void deleteById(ChatRoomId id) {
        jpaRepository.deleteById(id.value());
    }

}