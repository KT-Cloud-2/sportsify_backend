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
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ChatRoomJpaRepositoryTest extends RepositoryTestSupport {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 4, 12, 0);

    @Autowired
    private ChatRoomJpaRepository chatRoomJpaRepository;
    @Autowired
    private ChatRoomMemberJpaRepository chatRoomMemberJpaRepo;
    @Autowired
    private MemberJpaRepository memberJpaRepository;
    @Autowired
    private EntityManager em;

    private Long gameId;
    private Long member1Id;
    private Long member2Id;
    private Long room1Id;
    private Long room2Id;
    private Long directRoomId;

    @BeforeEach
    void setUp() {
        Member m1 = memberJpaRepository.save(Member.create("m1@test.com", "유저1", OAuthProvider.GOOGLE, "g-1"));
        Member m2 = memberJpaRepository.save(Member.create("m2@test.com", "유저2", OAuthProvider.GOOGLE, "g-2"));
        member1Id = m1.getId();
        member2Id = m2.getId();

        em.createNativeQuery("INSERT INTO stadiums(name) VALUES('테스트 구장')").executeUpdate();
        Long stadiumId = ((Number) em.createNativeQuery("SELECT lastval()").getSingleResult()).longValue();
        em.createNativeQuery(
                        "INSERT INTO games(stadium_id, sport_type, start_at, duration_minutes, status, created_at) " +
                                "VALUES(" + stadiumId + ", 'BASEBALL', now(), 180, 'SCHEDULED', now())")
                .executeUpdate();
        gameId = ((Number) em.createNativeQuery("SELECT lastval()").getSingleResult()).longValue();

        ChatRoomJpaEntity room1 = chatRoomJpaRepository.save(
                room("한화 VS LG", "GAME", gameId, "ACTIVE", member1Id));
        ChatRoomJpaEntity room2 = chatRoomJpaRepository.save(
                room("삼성 VS KIA", "GAME", gameId, "ACTIVE", member1Id));
        chatRoomJpaRepository.save(
                room("삭제된 방", "GAME", gameId, "DELETED", member1Id));
        ChatRoomJpaEntity directRoom = chatRoomJpaRepository.save(
                room("DM", "DIRECT", null, "ACTIVE", member1Id));
        chatRoomMemberJpaRepo.saveAll(List.of(
                member(directRoom.getId(), member1Id, "JOINED"),
                member(directRoom.getId(), member2Id, "JOINED")
        ));

        room1Id = room1.getId();
        room2Id = room2.getId();
        directRoomId = directRoom.getId();

        em.flush();
    }

    // ──────────────────────── findActiveByGameId ────────────────────────

    @Test
    @DisplayName("gameId로 ACTIVE 채팅방 목록을 조회한다")
    void findActiveByGameId_ACTIVE방_조회() {
        List<ChatRoomJpaEntity> result = chatRoomJpaRepository.findActiveByGameId(
                gameId, null, PageRequest.of(0, 10));

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(r -> "ACTIVE".equals(r.getStatus()));
        assertThat(result).allMatch(r -> gameId.equals(r.getGameId()));
    }

    @Test
    @DisplayName("cursor 미만 id를 가진 채팅방만 반환한다")
    void findActiveByGameId_cursor_필터() {
        List<ChatRoomJpaEntity> result = chatRoomJpaRepository.findActiveByGameId(
                gameId, room2Id, PageRequest.of(0, 10));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getId()).isEqualTo(room1Id);
    }

    // ──────────────────────── findExistingDirect ────────────────────────

    @Test
    @DisplayName("두 멤버 사이의 DIRECT 채팅방이 존재하면 id를 반환한다")
    void findExistingDirect_존재하는_DM방() {
        Optional<Long> result = chatRoomJpaRepository.findExistingDirect(member1Id, member2Id);

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(directRoomId);
    }

    @Test
    @DisplayName("두 멤버 사이의 DIRECT 채팅방이 없으면 빈 Optional을 반환한다")
    void findExistingDirect_없는_DM방() {
        Optional<Long> result = chatRoomJpaRepository.findExistingDirect(member1Id, 99L);

        assertThat(result).isEmpty();
    }

    // ──────────────────────── findByIdForUpdate ────────────────────────

    @Test
    @DisplayName("id로 채팅방을 비관적 락으로 조회한다")
    void findByIdForUpdate_락_조회() {
        Optional<ChatRoomJpaEntity> result = chatRoomJpaRepository.findByIdForUpdateWrite(room1Id);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(room1Id);
        assertThat(result.get().getStatus()).isEqualTo("ACTIVE");
    }

    // ──────────────────────── findActiveByRoomIds ────────────────────────

    @Test
    @DisplayName("roomIds와 type으로 ACTIVE 채팅방 목록을 조회한다")
    void findActiveByRoomIds_목록_조회() {
        List<ChatRoomJpaEntity> result = chatRoomJpaRepository.findActiveByRoomIds(
                List.of(room1Id, room2Id), "GAME", null, PageRequest.of(0, 10));

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(r -> "GAME".equals(r.getType()));
        assertThat(result).allMatch(r -> "ACTIVE".equals(r.getStatus()));
    }

    @Test
    @DisplayName("roomIds에 없는 id는 결과에 포함되지 않는다")
    void
    findActiveByRoomIds_id_필터() {
        List<ChatRoomJpaEntity> result = chatRoomJpaRepository.findActiveByRoomIds(
                List.of(room1Id), "GAME", null, PageRequest.of(0, 10));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getId()).isEqualTo(room1Id);
    }

    @Test
    @DisplayName("cursor 미만 id를 가진 채팅방만 반환한다")
    void findActiveByRoomIds_cursor_필터() {
        List<ChatRoomJpaEntity> result = chatRoomJpaRepository.findActiveByRoomIds(
                List.of(room1Id, room2Id), "GAME", room2Id, PageRequest.of(0, 10));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getId()).isEqualTo(room1Id);
    }

    // ──────────────────────── 픽스처 헬퍼 ────────────────────────

    private ChatRoomJpaEntity room(String name, String type, Long gameId, String status, Long createdBy) {
        return new ChatRoomJpaEntity(null, name, type, null, gameId, NOW, NOW, status, createdBy);
    }

    private ChatRoomMemberJpaEntity member(Long roomId, Long memberId, String status) {
        return new ChatRoomMemberJpaEntity(null, roomId, memberId, status, true, NOW, NOW, null);
    }
}
