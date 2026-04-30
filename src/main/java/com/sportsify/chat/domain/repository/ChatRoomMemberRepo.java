package com.sportsify.chat.domain.repository;

import com.sportsify.chat.domain.model.chatRoom.ChatRoomId;
import com.sportsify.chat.domain.model.chatRoom.MemberId;
import com.sportsify.chat.domain.model.chatRoomMember.ChatRoomMember;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * ChatRoomMember Aggregate 영속화 포트
 */
public interface ChatRoomMemberRepo {

    ChatRoomMember save(ChatRoomMember member);

    ChatRoomMember saveAndFlush(ChatRoomMember member);

    List<ChatRoomMember> saveAll(List<ChatRoomMember> members);

    Optional<ChatRoomMember> findByRoomAndMember(ChatRoomId roomId, MemberId memberId);

    Optional<ChatRoomMember> findByRoomAndMemberWithStatus(ChatRoomId roomId, MemberId memberId, List<String> statuses);


    List<ChatRoomMember> findActiveByRoom(ChatRoomId roomId);

    List<ChatRoomMember> findActiveByMember(MemberId memberId);

    long countActiveByRoom(ChatRoomId roomId);

    Map<ChatRoomId, Long> countActiveByRooms(List<ChatRoomId> roomIds);


    boolean existsJoinedByRoomAndMember(ChatRoomId roomId, MemberId memberId);

    void leaveAllMembersByRoom(ChatRoomId roomId, LocalDateTime now);
}