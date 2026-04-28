package com.sportsify.chat.domain.repository;

import com.sportsify.chat.domain.model.chatRoom.ChatRoomId;
import com.sportsify.chat.domain.model.message.Message;
import com.sportsify.chat.domain.model.message.MessageId;

import java.util.List;
import java.util.Optional;

/**
 * Message Aggregate 영속화 포트
 */
public interface MessageRepo {

    Message save(Message message);

    Optional<Message> findById(MessageId id);

    List<Message> findByRoomBefore(ChatRoomId roomId, Long beforeMessageId, int limit);

    long countAfter(ChatRoomId roomId, Long afterMessageId);
}