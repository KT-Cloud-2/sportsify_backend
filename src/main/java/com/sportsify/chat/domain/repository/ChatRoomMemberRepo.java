package com.sportsify.chat.domain.repository;

import com.sportsify.chat.domain.model.chatRoom.ChatRoomId;
import com.sportsify.chat.domain.model.chatRoom.MemberId;
import com.sportsify.chat.domain.model.chatRoomMember.ChatRoomMember;

import java.util.List;
import java.util.Optional;

/**
 * ChatRoomMember Aggregate 영속화 포트
 */
public interface ChatRoomMemberRepo {

    ChatRoomMember save(ChatRoomMember member);

    Optional<ChatRoomMember> findByRoomAndMember(ChatRoomId roomId, MemberId memberId);

    List<ChatRoomMember> findActiveByRoom(ChatRoomId roomId);

    List<ChatRoomMember> findActiveByMember(MemberId memberId);

    long countActiveByRoom(ChatRoomId roomId);

    boolean existsByRoomAndMember(ChatRoomId roomId, MemberId memberId);
}