package com.sportsify.chat.domain.repository;


import com.sportsify.chat.domain.model.chatRoom.ChatRoom;
import com.sportsify.chat.domain.model.chatRoom.ChatRoomId;

import java.util.Optional;

/**
 * ChatRoom Aggregate 영속화 포트
 */
public interface ChatRoomRepo {

    ChatRoom save(ChatRoom chatRoom);

    Optional<ChatRoom> findById(ChatRoomId id);

    Optional<ChatRoom> findByGameId(Long gameId);

    boolean existsById(ChatRoomId id);

    boolean existByCreatorIdAndInviteId(Long creatorId, Long inviteId);

    void deleteById(ChatRoomId id);
}