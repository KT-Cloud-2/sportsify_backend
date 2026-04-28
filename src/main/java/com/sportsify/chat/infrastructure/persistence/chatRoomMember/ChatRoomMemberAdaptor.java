package com.sportsify.chat.infrastructure.persistence.chatRoomMember;

import com.sportsify.chat.domain.model.chatRoom.ChatRoomId;
import com.sportsify.chat.domain.model.chatRoom.MemberId;
import com.sportsify.chat.domain.model.chatRoomMember.ChatRoomMember;
import com.sportsify.chat.domain.repository.ChatRoomMemberRepo;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class ChatRoomMemberAdaptor implements ChatRoomMemberRepo {

    private final ChatRoomMemberJpaRepo jpaRepo;
    private final ChatRoomMemberMapper mapper;

    public ChatRoomMemberAdaptor(ChatRoomMemberJpaRepo jpaRepo, ChatRoomMemberMapper mapper) {
        this.jpaRepo = jpaRepo;
        this.mapper = mapper;
    }

    @Override
    public ChatRoomMember save(ChatRoomMember member) {
        ChatRoomMemberJpaEntity entity;
        if (member.getId() == null) {
            entity = mapper.toNewJpaEntity(member);
        } else {
            Long id = member.getId();
            entity = jpaRepo.findById(id)
                    .orElseThrow(() -> new IllegalStateException(
                            "ChatRoomMember not found for update: id=" + id));
            mapper.applyToJpa(entity, member);
        }
        ChatRoomMemberJpaEntity saved = jpaRepo.save(entity);
        if (member.getId() == null) {
            member.assignId(saved.getId());
        }
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<ChatRoomMember> findByRoomAndMember(ChatRoomId roomId, MemberId memberId) {
        return jpaRepo.findByRoomIdAndMemberId(roomId.value(), memberId.value())
                .map(mapper::toDomain);
    }

    @Override
    public List<ChatRoomMember> findActiveByRoom(ChatRoomId roomId) {
        return jpaRepo.findActiveByRoomId(roomId.value())
                .stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<ChatRoomMember> findActiveByMember(MemberId memberId) {
        return jpaRepo.findActiveByMemberId(memberId.value())
                .stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public long countActiveByRoom(ChatRoomId roomId) {
        return jpaRepo.countActiveByRoomId(roomId.value());
    }

    @Override
    public boolean existsByRoomAndMember(ChatRoomId roomId, MemberId memberId) {
        return jpaRepo.existsByRoomIdAndMemberId(roomId.value(), memberId.value());
    }
}
