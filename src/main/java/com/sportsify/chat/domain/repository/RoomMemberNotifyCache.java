package com.sportsify.chat.domain.repository;

import com.sportsify.chat.domain.model.chatRoomMember.ChatRoomMember;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface RoomMemberNotifyCache {

    void put(Long roomId, Long memberId, boolean notificationEnabled);

    void remove(Long roomId, Long memberId);

    void evict(Long roomId);

    Optional<Set<Long>> getNotifiableMemberIds(Long roomId);

    void populate(Long roomId, List<ChatRoomMember> members);
}
