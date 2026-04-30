package com.sportsify.chat.infrastructure.persistence.chatRoomMember;

import com.sportsify.chat.domain.model.chatRoom.ChatRoomId;
import com.sportsify.chat.domain.model.chatRoom.MemberId;
import com.sportsify.chat.domain.model.chatRoomMember.ChatRoomMember;
import com.sportsify.chat.domain.repository.ChatRoomMemberRepo;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
        ChatRoomMemberJpaEntity entity = prepareEntity(member);
        ChatRoomMemberJpaEntity saved = jpaRepo.save(entity);
        if (member.getId() == null) member.assignId(saved.getId());
        return mapper.toDomain(saved);
    }

    @Override
    public ChatRoomMember saveAndFlush(ChatRoomMember member) {
        ChatRoomMemberJpaEntity entity = prepareEntity(member);
        ChatRoomMemberJpaEntity saved = jpaRepo.saveAndFlush(entity);
        if (member.getId() == null) member.assignId(saved.getId());
        return mapper.toDomain(saved);
    }

    private ChatRoomMemberJpaEntity prepareEntity(ChatRoomMember member) {
        if (member.getId() == null) {
            return mapper.toNewJpaEntity(member);
        }
        Long id = member.getId();
        ChatRoomMemberJpaEntity entity = jpaRepo.findById(id)
                .orElseThrow(() -> new IllegalStateException(
                        "ChatRoomMember not found for update: id=" + id));
        mapper.applyToJpa(entity, member);
        return entity;
    }

    @Override
    public List<ChatRoomMember> saveAll(List<ChatRoomMember> members) {
        List<ChatRoomMemberJpaEntity> entities = members.stream()
                .map(member -> {
                    if (member.getId() == null) {
                        return mapper.toNewJpaEntity(member);
                    }
                    Long id = member.getId();
                    ChatRoomMemberJpaEntity entity = jpaRepo.findById(id)
                            .orElseThrow(() -> new IllegalStateException(
                                    "ChatRoomMember not found for update: id=" + id));
                    mapper.applyToJpa(entity, member);
                    return entity;
                })
                .toList();

        return jpaRepo.saveAll(entities).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public Optional<ChatRoomMember> findByRoomAndMember(ChatRoomId roomId, MemberId memberId) {
        return jpaRepo.findByRoomIdAndMemberId(roomId.value(), memberId.value())
                .map(mapper::toDomain);
    }

    @Override
    public Optional<ChatRoomMember> findByRoomAndMemberWithStatus(ChatRoomId roomId, MemberId memberId, List<String> statuses) {
        return jpaRepo.findByRoomIdAndMemberId(roomId.value(), memberId.value(), statuses)
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
    public Map<ChatRoomId, Long> countActiveByRooms(List<ChatRoomId> rooms) {
        if (rooms.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = rooms.stream().map(ChatRoomId::value).toList();
        return jpaRepo.countActiveByRoomIds(ids).stream()
                .collect(Collectors.toMap(
                        row -> ChatRoomId.of((Long) row[0]),
                        row -> (Long) row[1]
                ));
    }

    @Override
    public boolean existsJoinedByRoomAndMember(ChatRoomId roomId, MemberId memberId) {
        return jpaRepo.existsByRoomIdAndMemberIdAndStatus(roomId.value(), memberId.value(), "JOINED");
    }

    /**
     * fitler 없이 모든 user를 delete 시키기 때문에 주의가 필요합니다
     */
    @Override
    public void leaveAllMembersByRoom(ChatRoomId roomId, LocalDateTime now) {
        jpaRepo.leaveAllActiveByRoomId(roomId.value(), now);
    }
}
