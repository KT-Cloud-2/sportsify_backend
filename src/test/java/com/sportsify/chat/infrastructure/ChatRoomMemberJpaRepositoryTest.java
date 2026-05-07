package com.sportsify.chat.infrastructure;

import com.sportsify.chat.infrastructure.persistence.chatRoom.ChatRoomJpaEntity;
import com.sportsify.chat.infrastructure.persistence.chatRoom.ChatRoomJpaRepository;
import com.sportsify.chat.infrastructure.persistence.chatRoomMember.ChatRoomMemberJpaEntity;
import com.sportsify.chat.infrastructure.persistence.chatRoomMember.ChatRoomMemberJpaRepository;
import com.sportsify.member.domain.model.Member;
import com.sportsify.member.domain.model.OAuthProvider;
import com.sportsify.member.infrastructure.repository.MemberJpaRepository;
import com.sportsify.support.RepositoryTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ChatRoomMemberJpaRepositoryTest extends RepositoryTestSupport {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 4, 12, 0);
    @Autowired
    private ChatRoomMemberJpaRepository chatRoomMemberJpaRepo;
    @Autowired
    private ChatRoomJpaRepository chatRoomJpaRepository;
    @Autowired
    private MemberJpaRepository memberJpaRepository;
    @Autowired
    private EntityManager em;
    private Long room1Id;
    private Long room2Id;
    private Long member1Id;
    private Long member2Id;

    @BeforeEach
    void setUp() {
        Member m1 = memberJpaRepository.save(Member.create("m1@test.com", "유저1", OAuthProvider.GOOGLE, "g-1"));
        Member m2 = memberJpaRepository.save(Member.create("m2@test.com", "유저2", OAuthProvider.GOOGLE, "g-2"));

        ChatRoomJpaEntity room1 = chatRoomJpaRepository.save(room("방1", m1.getId()));
        ChatRoomJpaEntity room2 = chatRoomJpaRepository.save(room("방2", m1.getId()));

        chatRoomMemberJpaRepo.saveAll(List.of(
                member(room1.getId(), m1.getId(), "JOINED"),
                member(room1.getId(), m2.getId(), "INVITED"),
                member(room2.getId(), m1.getId(), "JOINED")
        ));

        room1Id = room1.getId();
        room2Id = room2.getId();
        member1Id = m1.getId();
        member2Id = m2.getId();

        em.flush();
    }

    // ──────────────────────── findByRoomIdAndMemberId ────────────────────────

    @Test
    @DisplayName("roomId와 memberId로 JOINED/INVITED 멤버를 조회한다")
    void findByRoomIdAndMemberId_기본조회() {
        Optional<ChatRoomMemberJpaEntity> result = chatRoomMemberJpaRepo.findByRoomIdAndMemberId(room1Id, member1Id);

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo("JOINED");
    }

    @Test
    @DisplayName("status 목록을 지정하면 해당 status의 멤버만 조회한다")
    void findByRoomIdAndMemberId_status_필터() {
        Optional<ChatRoomMemberJpaEntity> notFound = chatRoomMemberJpaRepo.findByRoomIdAndMemberId(
                room1Id, member2Id, List.of("JOINED"));
        Optional<ChatRoomMemberJpaEntity> found = chatRoomMemberJpaRepo.findByRoomIdAndMemberId(
                room1Id, member2Id, List.of("INVITED"));

        assertThat(notFound).isEmpty();
        assertThat(found).isPresent();
    }

    // ──────────────────────── findActiveByRoomId ────────────────────────

    @Test
    @DisplayName("roomId로 JOINED/INVITED 멤버 목록을 조회한다")
    void findActiveByRoomId_목록조회() {
        List<ChatRoomMemberJpaEntity> result = chatRoomMemberJpaRepo.findActiveByRoomId(room1Id);

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(m -> List.of("JOINED", "INVITED").contains(m.getStatus()));
    }

    // ──────────────────────── findActiveByMemberId ────────────────────────

    @Test
    @DisplayName("memberId로 JOINED/INVITED 상태의 멤버십 목록을 조회한다")
    void findActiveByMemberId_멤버십목록() {
        List<ChatRoomMemberJpaEntity> result = chatRoomMemberJpaRepo.findActiveByMemberId(member1Id);

        assertThat(result).hasSize(2);
        assertThat(result.stream().map(ChatRoomMemberJpaEntity::getRoomId).toList())
                .containsExactlyInAnyOrder(room1Id, room2Id);
    }

    // ──────────────────────── countActiveByRoomId ────────────────────────

    @Test
    @DisplayName("roomId로 JOINED/INVITED 멤버 수를 반환한다")
    void countActiveByRoomId_카운트() {
        long count = chatRoomMemberJpaRepo.countActiveByRoomId(room1Id);

        assertThat(count).isEqualTo(2);
    }

    // ──────────────────────── existsByRoomIdAndMemberIdAndStatus ────────────────────────

    @Test
    @DisplayName("조건에 맞는 멤버가 존재하면 true를 반환한다")
    void existsByRoomIdAndMemberIdAndStatus_존재함() {
        assertThat(chatRoomMemberJpaRepo.existsByRoomIdAndMemberIdAndStatus(
                room1Id, member1Id, "JOINED")).isTrue();
    }

    @Test
    @DisplayName("조건에 맞는 멤버가 없으면 false를 반환한다")
    void existsByRoomIdAndMemberIdAndStatus_없음() {
        assertThat(chatRoomMemberJpaRepo.existsByRoomIdAndMemberIdAndStatus(
                room1Id, member2Id, "JOINED")).isFalse();
    }

    // ──────────────────────── countActiveByRoomIds ────────────────────────

    @Test
    @DisplayName("roomId 목록별 JOINED/INVITED 멤버 수를 일괄 반환한다")
    void countActiveByRoomIds_일괄카운트() {
        List<Object[]> raw = chatRoomMemberJpaRepo.countActiveByRoomIds(List.of(room1Id, room2Id));
        Map<Long, Long> countMap = raw.stream()
                .collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));

        assertThat(countMap).containsEntry(room1Id, 2L);
        assertThat(countMap).containsEntry(room2Id, 1L);
    }

    // ──────────────────────── leaveAllActiveByRoomId ────────────────────────

    @Test
    @DisplayName("roomId의 JOINED/INVITED 멤버를 전원 DELETED 처리한다")
    void leaveAllActiveByRoomId_전원퇴장() {
        chatRoomMemberJpaRepo.leaveAllActiveByRoomId(room1Id, NOW);

        long remaining = chatRoomMemberJpaRepo.countActiveByRoomId(room1Id);
        assertThat(remaining).isEqualTo(0);
    }

    // ──────────────────────── 픽스처 헬퍼 ────────────────────────

    private ChatRoomJpaEntity room(String name, Long createdBy) {
        return new ChatRoomJpaEntity(null, name, "GAME", null, null, NOW, NOW, "ACTIVE", createdBy);
    }

    private ChatRoomMemberJpaEntity member(Long roomId, Long memberId, String status) {
        return new ChatRoomMemberJpaEntity(null, roomId, memberId, status, true, NOW, NOW, null);
    }
}
