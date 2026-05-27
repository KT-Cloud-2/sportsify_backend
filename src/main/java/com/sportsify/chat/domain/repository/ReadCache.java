package com.sportsify.chat.domain.repository;

import com.sportsify.chat.domain.model.chatRoom.ChatRoomId;
import com.sportsify.chat.domain.model.chatRoom.MemberId;
import com.sportsify.chat.domain.model.message.MessageId;

import java.util.List;

public interface ReadCache {

    void put(ChatRoomId roomId, MemberId memberId, MessageId lastReadMessageId);

    List<ReadEntry> drainAll();

    record ReadEntry(ChatRoomId roomId, MemberId memberId, MessageId messageId) {}
}
