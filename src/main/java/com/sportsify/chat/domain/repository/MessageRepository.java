package com.sportsify.chat.domain.repository;

import com.sportsify.chat.domain.model.chatRoom.ChatRoomId;
import com.sportsify.chat.domain.model.chatRoom.MemberId;
import com.sportsify.chat.domain.model.message.Message;
import com.sportsify.chat.domain.model.message.MessageId;

import java.util.List;
import java.util.Optional;

/**
 * Message Aggregate 영속화 포트
 */
public interface MessageRepository {

    Message save(Message message);

    Optional<Message> findById(MessageId id);

    Optional<Message> findByIdForUpdate(MessageId id);

    List<Message> findByRoomBefore(ChatRoomId roomId, Long beforeMessageId, int limit);


    List<Message> findByRoomAndMemberBefore(ChatRoomId roomId, MemberId memberId, Long beforeMessageId, int limit);

    long countAfter(ChatRoomId roomId, Long afterMessageId);


    List<Message> findMyLatestByRooms(List<ChatRoomId> roomIds, MemberId memberId);
}
