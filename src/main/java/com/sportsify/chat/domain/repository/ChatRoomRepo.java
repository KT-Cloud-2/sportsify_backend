package com.sportsify.chat.domain.repository;


import com.sportsify.chat.domain.model.chatRoom.ChatRoom;
import com.sportsify.chat.domain.model.chatRoom.ChatRoomId;
import com.sportsify.chat.domain.model.chatRoom.ChatRoomType;
import com.sportsify.chat.domain.model.chatRoom.GameId;

import java.util.List;
import java.util.Optional;

/**
 * ChatRoom Aggregate 영속화 포트
 */
public interface ChatRoomRepo {

    ChatRoom save(ChatRoom chatRoom);

    Optional<ChatRoom> findById(ChatRoomId id);

    Optional<ChatRoom> findByIdForUpdate(ChatRoomId id);

    List<ChatRoom> findActiveByGameId(GameId gameId, Long cursor, int limit);

    boolean existsById(ChatRoomId id);

    Optional<Long> existByCreatorIdAndInviteId(Long creatorId, Long inviteId);

    void deleteById(ChatRoomId id);

    List<ChatRoom> findActiveByRoomIds(List<ChatRoomId> roomIds, ChatRoomType type, Long cursor, int limit);
}