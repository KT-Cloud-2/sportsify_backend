package com.sportsify.chat.infrastructure.persistence.chatRoom;

import com.sportsify.chat.domain.model.chatRoom.ChatRoom;
import com.sportsify.chat.domain.model.chatRoom.ChatRoomId;
import com.sportsify.chat.domain.model.chatRoom.ChatRoomType;
import com.sportsify.chat.domain.model.chatRoom.GameId;
import com.sportsify.chat.domain.repository.ChatRoomRepo;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * ChatRoomRepo(도메인 포트) 의 JPA 어댑터 구현체.
 */
@Repository
public class ChatRoomAdaptor implements ChatRoomRepo {

    private final ChatRoomJpaRepo jpaRepository;
    private final ChatRoomMapper mapper;

    public ChatRoomAdaptor(ChatRoomJpaRepo jpaRepository,
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
    public Optional<ChatRoom> findByIdForUpdate(ChatRoomId id) {
        return jpaRepository.findByIdForUpdate(id.value()).map(mapper::toDomain);
    }


    @Override
    public List<ChatRoom> findActiveByGameId(GameId gameId, Long cursor, int limit) {
        if (gameId == null) {
            return List.of();
        }
        return jpaRepository.findActiveByGameId(gameId.value(), cursor, PageRequest.of(0, limit))
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public boolean existsById(ChatRoomId id) {
        return jpaRepository.existsById(id.value());
    }

    @Override
    public Optional<Long> existByCreatorIdAndInviteId(Long creatorId, Long inviteId) {
        return jpaRepository.findExistingDirect(creatorId, inviteId);
    }

    @Override
    public void deleteById(ChatRoomId id) {
        jpaRepository.deleteById(id.value());
    }

    @Override
    public List<ChatRoom> findActiveByRoomIds(List<ChatRoomId> roomIds, ChatRoomType type, Long cursor, int limit) {
        if (roomIds.isEmpty()) {
            return List.of();
        }
        List<Long> ids = roomIds.stream().map(ChatRoomId::value).toList();
        return jpaRepository.findActiveByRoomIds(ids, type.name(), cursor, PageRequest.of(0, limit))
                .stream().map(mapper::toDomain).toList();
    }

}