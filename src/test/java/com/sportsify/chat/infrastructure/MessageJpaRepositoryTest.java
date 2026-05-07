package com.sportsify.chat.infrastructure;

import com.sportsify.chat.infrastructure.persistence.chatRoom.ChatRoomJpaEntity;
import com.sportsify.chat.infrastructure.persistence.chatRoom.ChatRoomJpaRepository;
import com.sportsify.chat.infrastructure.persistence.chatRoomMember.ChatRoomMemberJpaEntity;
import com.sportsify.chat.infrastructure.persistence.chatRoomMember.ChatRoomMemberJpaRepository;
import com.sportsify.chat.infrastructure.persistence.message.MessageJpaEntity;
import com.sportsify.chat.infrastructure.persistence.message.MessageJpaRepository;
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

class MessageJpaRepositoryTest extends RepositoryTestSupport {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 6, 12, 0);


    @Autowired
    private MessageJpaRepository messageJpaRepository;
    @Autowired
    private ChatRoomJpaRepository chatRoomJpaRepository;
    @Autowired
    private ChatRoomMemberJpaRepository chatRoomMemberJpaRepository;
    @Autowired
    private MemberJpaRepository memberJpaRepository;
    @Autowired
    private EntityManager em;

    private Long room1Id;
    private Long room2Id;
    private Long sender1Id;
    private Long sender2Id;

    private Long msg1Id; // room1, sender1, ACTIVE
    private Long msg2Id; // room1, sender1, ACTIVE
    private Long msg3Id; // room1, sender2, ACTIVE
    private Long msg4Id; // room1, sender1, DELETED

    private ChatRoomJpaEntity chatRoom(String name, Long createdBy) {
        return new ChatRoomJpaEntity(null, name, "GAME", null, null, NOW, NOW, "ACTIVE", createdBy);
    }

    private ChatRoomMemberJpaEntity chatRoomMember(Long roomId, Long createdBy) {
        return new ChatRoomMemberJpaEntity(null, roomId, createdBy, "JOINED", true, NOW, NOW, null);
    }

    @BeforeEach
    void setUp() {
        Member m1 = memberJpaRepository.save(Member.create("s1@test.com", "발신자1", OAuthProvider.GOOGLE, "g-s1"));
        Member m2 = memberJpaRepository.save(Member.create("s2@test.com", "발신자2", OAuthProvider.GOOGLE, "g-s2"));
        sender1Id = m1.getId();
        sender2Id = m2.getId();
        ChatRoomJpaEntity room1 = chatRoomJpaRepository.save(chatRoom("채팅방1", sender1Id));
        ChatRoomJpaEntity room2 = chatRoomJpaRepository.save(chatRoom("채팅방2", sender1Id));
        room1Id = room1.getId();
        room2Id = room2.getId();
        chatRoomMemberJpaRepository.saveAll(List.of(
                chatRoomMember(room1Id, sender1Id),
                chatRoomMember(room1Id, sender2Id),
                chatRoomMember(room2Id, sender1Id)
        ));
        MessageJpaEntity msg1 = messageJpaRepository.save(message(room1Id, sender1Id, "첫 번째 메시지", "ACTIVE"));
        MessageJpaEntity msg2 = messageJpaRepository.save(message(room1Id, sender1Id, "두 번째 메시지", "ACTIVE"));
        MessageJpaEntity msg3 = messageJpaRepository.save(message(room1Id, sender2Id, "다른 유저 메시지", "ACTIVE"));
        MessageJpaEntity msg4 = messageJpaRepository.save(message(room1Id, sender1Id, "삭제된 메시지", "DELETED"));
        messageJpaRepository.save(message(room2Id, sender1Id, "다른 방 메시지", "ACTIVE"));

        msg1Id = msg1.getId();
        msg2Id = msg2.getId();
        msg3Id = msg3.getId();
        msg4Id = msg4.getId();

        em.flush();
    }

    // ──────────────────────── findLatest ────────────────────────

    @Test
    @DisplayName("roomId로 최신 메시지 목록을 id DESC로 조회한다")
    void findLatest_최신_메시지_조회() {
        List<MessageJpaEntity> result = messageJpaRepository.findLatest(room1Id, PageRequest.of(0, 10));

        assertThat(result).hasSize(4);
        assertThat(result).allMatch(m -> room1Id.equals(m.getRoomId()));
        assertThat(result.getFirst().getId()).isGreaterThan(result.getLast().getId());
    }

    @Test
    @DisplayName("limit 만큼만 메시지를 반환한다")
    void findLatest_limit_적용() {
        List<MessageJpaEntity> result = messageJpaRepository.findLatest(room1Id, PageRequest.of(0, 2));

        assertThat(result).hasSize(2);
        assertThat(result.getFirst().getId()).isEqualTo(msg4Id);
        assertThat(result.get(1).getId()).isEqualTo(msg3Id);
    }

    // ──────────────────────── findBefore ────────────────────────

    @Test
    @DisplayName("cursor(beforeMessageId) 미만 id를 가진 메시지를 반환한다")
    void findBefore_cursor_필터() {
        List<MessageJpaEntity> result = messageJpaRepository.findBefore(room1Id, msg3Id, PageRequest.of(0, 10));

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(m -> m.getId() < msg3Id);
    }

    @Test
    @DisplayName("cursor보다 큰 id만 존재하면 빈 리스트를 반환한다")
    void findBefore_결과_없음() {
        List<MessageJpaEntity> result = messageJpaRepository.findBefore(room1Id, msg1Id, PageRequest.of(0, 10));

        assertThat(result).isEmpty();
    }

    // ──────────────────────── countAfter ────────────────────────

    @Test
    @DisplayName("afterMessageId 이후 ACTIVE 메시지 수를 반환한다")
    void countAfter_ACTIVE_메시지_카운트() {
        long count = messageJpaRepository.countAfter(room1Id, msg1Id);

        // msg2(ACTIVE), msg3(ACTIVE) → DELETED(msg4)는 제외
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("DELETED 상태 메시지는 countAfter에 포함되지 않는다")
    void countAfter_DELETED_제외() {
        long count = messageJpaRepository.countAfter(room1Id, msg3Id);

        // msg4는 DELETED이므로 0
        assertThat(count).isZero();
    }

    // ──────────────────────── countAll ────────────────────────

    @Test
    @DisplayName("roomId의 ACTIVE 메시지 전체 수를 반환한다")
    void countAll_ACTIVE_전체_카운트() {
        long count = messageJpaRepository.countAll(room1Id);

        // msg1, msg2, msg3 — msg4(DELETED) 제외
        assertThat(count).isEqualTo(3);
    }

    @Test
    @DisplayName("메시지가 없는 방은 0을 반환한다")
    void countAll_빈_방() {
        long count = messageJpaRepository.countAll(99L);

        assertThat(count).isZero();
    }

    // ──────────────────────── findLatestBySenderAndRoom ────────────────────────

    @Test
    @DisplayName("roomId와 senderId로 해당 발신자의 메시지만 조회한다")
    void findLatestBySenderAndRoom_발신자_필터() {
        List<MessageJpaEntity> result = messageJpaRepository.findLatestBySenderAndRoom(
                room1Id, sender1Id, PageRequest.of(0, 10));

        assertThat(result).hasSize(3); // msg1, msg2, msg4
        assertThat(result).allMatch(m -> sender1Id.equals(m.getSenderId()));
        assertThat(result).allMatch(m -> room1Id.equals(m.getRoomId()));
    }

    @Test
    @DisplayName("다른 방의 메시지는 결과에 포함되지 않는다")
    void findLatestBySenderAndRoom_다른_방_제외() {
        List<MessageJpaEntity> result = messageJpaRepository.findLatestBySenderAndRoom(
                room2Id, sender1Id, PageRequest.of(0, 10));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getRoomId()).isEqualTo(room2Id);
    }

    // ──────────────────────── findBeforeBySenderAndRoom ────────────────────────

    @Test
    @DisplayName("cursor 미만 id를 가진 발신자 메시지만 반환한다")
    void findBeforeBySenderAndRoom_cursor_필터() {
        List<MessageJpaEntity> result = messageJpaRepository.findBeforeBySenderAndRoom(
                room1Id, sender1Id, msg2Id, PageRequest.of(0, 10));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getId()).isEqualTo(msg1Id);
    }

    @Test
    @DisplayName("cursor 이전에 해당 발신자 메시지가 없으면 빈 리스트를 반환한다")
    void findBeforeBySenderAndRoom_결과_없음() {
        List<MessageJpaEntity> result = messageJpaRepository.findBeforeBySenderAndRoom(
                room1Id, sender1Id, msg1Id, PageRequest.of(0, 10));

        assertThat(result).isEmpty();
    }

    // ──────────────────────── findByIdForUpdate ────────────────────────

    @Test
    @DisplayName("messageId로 비관적 쓰기 락을 걸고 메시지를 조회한다")
    void findByIdForUpdate_락_조회() {
        Optional<MessageJpaEntity> result = messageJpaRepository.findByIdForUpdate(msg1Id);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(msg1Id);
        assertThat(result.get().getContent()).isEqualTo("첫 번째 메시지");
    }

    @Test
    @DisplayName("존재하지 않는 id는 빈 Optional을 반환한다")
    void findByIdForUpdate_존재하지_않음() {
        Optional<MessageJpaEntity> result = messageJpaRepository.findByIdForUpdate(99999L);

        assertThat(result).isEmpty();
    }

    // ──────────────────────── 픽스처 헬퍼 ────────────────────────

    private MessageJpaEntity message(Long roomId, Long senderId, String content, String status) {
        return new MessageJpaEntity(null, roomId, senderId, content, "TEXT", status, NOW);
    }
}
