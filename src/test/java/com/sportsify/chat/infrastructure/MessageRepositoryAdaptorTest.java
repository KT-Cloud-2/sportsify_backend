package com.sportsify.chat.infrastructure;

import com.sportsify.chat.domain.model.chatRoom.ChatRoomId;
import com.sportsify.chat.domain.model.chatRoom.MemberId;
import com.sportsify.chat.domain.model.message.Message;
import com.sportsify.chat.infrastructure.persistence.chatRoom.ChatRoomJpaEntity;
import com.sportsify.chat.infrastructure.persistence.chatRoom.ChatRoomJpaRepository;
import com.sportsify.chat.infrastructure.persistence.chatRoomMember.ChatRoomMemberJpaEntity;
import com.sportsify.chat.infrastructure.persistence.chatRoomMember.ChatRoomMemberJpaRepository;
import com.sportsify.chat.infrastructure.persistence.message.MessageJpaEntity;
import com.sportsify.chat.infrastructure.persistence.message.MessageJpaRepository;
import com.sportsify.chat.infrastructure.persistence.message.MessageRepositoryAdaptor;
import com.sportsify.member.domain.model.Member;
import com.sportsify.member.domain.model.OAuthProvider;
import com.sportsify.member.infrastructure.repository.MemberJpaRepository;
import com.sportsify.support.RepositoryTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.dao.InvalidDataAccessApiUsageException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("[단위] MessageRepositoryAdaptor 커서 기반 페이지네이션 분기 테스트")
class MessageRepositoryAdaptorTest extends RepositoryTestSupport {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 6, 12, 0);
    private static final Instant INSTANT_NOW = Instant.parse("2026-05-06T12:00:00Z");

    @Autowired
    private MessageRepositoryAdaptor adaptor;
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

    private ChatRoomId roomId;
    private MemberId sender1;
    private MemberId sender2;
    private Long msg1Id;
    private Long msg2Id;
    private Long msg3Id;

    @BeforeEach
    void setUp() {
        Member m1 = memberJpaRepository.save(Member.create("a1@test.com", "발신자1", OAuthProvider.GOOGLE, "g-a1"));
        Member m2 = memberJpaRepository.save(Member.create("a2@test.com", "발신자2", OAuthProvider.GOOGLE, "g-a2"));
        sender1 = MemberId.of(m1.getId());
        sender2 = MemberId.of(m2.getId());

        ChatRoomJpaEntity room = chatRoomJpaRepository.save(
                new ChatRoomJpaEntity(null, "테스트방", "GAME", null, null, NOW, NOW, "ACTIVE", m1.getId()));
        roomId = ChatRoomId.of(room.getId());

        chatRoomMemberJpaRepository.saveAll(List.of(
                new ChatRoomMemberJpaEntity(null, room.getId(), m1.getId(), "JOINED", true, NOW, NOW, null),
                new ChatRoomMemberJpaEntity(null, room.getId(), m2.getId(), "JOINED", true, NOW, NOW, null)
        ));

        MessageJpaEntity msg1 = messageJpaRepository.save(message(room.getId(), m1.getId(), "첫 번째"));
        MessageJpaEntity msg2 = messageJpaRepository.save(message(room.getId(), m1.getId(), "두 번째"));
        MessageJpaEntity msg3 = messageJpaRepository.save(message(room.getId(), m2.getId(), "세 번째"));
        msg1Id = msg1.getId();
        msg2Id = msg2.getId();
        msg3Id = msg3.getId();

        em.flush();
    }

    // ──────────────────────── findByRoomBefore ────────────────────────

    @Test
    @DisplayName("cursor가 null이면 최신 메시지부터 조회한다 (findLatest 경로)")
    void findByRoomBefore_cursor_null_최신부터_조회() {
        List<Message> result = adaptor.findByRoomBefore(roomId, null, 10);

        assertThat(result).hasSize(3);
        assertThat(result.getFirst().getId().value()).isEqualTo(msg3Id);
    }

    @Test
    @DisplayName("cursor가 있으면 해당 id 미만 메시지를 조회한다 (findBefore 경로)")
    void findByRoomBefore_cursor_있음_이전_메시지_조회() {
        List<Message> result = adaptor.findByRoomBefore(roomId, msg3Id, 10);

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(m -> m.getId().value() < msg3Id);
    }

    @Test
    @DisplayName("cursor 이전에 메시지가 없으면 빈 리스트를 반환한다")
    void findByRoomBefore_cursor_이전_메시지_없음() {
        List<Message> result = adaptor.findByRoomBefore(roomId, msg1Id, 10);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("limit이 0 이하면 예외를 던진다")
    void findByRoomBefore_limit_0이하_예외() {
        assertThatThrownBy(() -> adaptor.findByRoomBefore(roomId, null, 0))
                .isInstanceOf(InvalidDataAccessApiUsageException.class)
                .hasMessageContaining("limit must be positive");
    }

    // ──────────────────────── findByRoomAndMemberBefore ────────────────────────

    @Test
    @DisplayName("cursor가 null이면 발신자의 최신 메시지부터 조회한다 (findLatestBySenderAndRoom 경로)")
    void findByRoomAndMemberBefore_cursor_null_최신부터_조회() {
        List<Message> result = adaptor.findByRoomAndMemberBefore(roomId, sender1, null, 10);

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(m -> m.getSenderId().equals(sender1));
    }

    @Test
    @DisplayName("cursor가 있으면 발신자의 해당 id 미만 메시지를 조회한다 (findBeforeBySenderAndRoom 경로)")
    void findByRoomAndMemberBefore_cursor_있음_이전_메시지_조회() {
        List<Message> result = adaptor.findByRoomAndMemberBefore(roomId, sender1, msg2Id, 10);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getId().value()).isEqualTo(msg1Id);
    }

    @Test
    @DisplayName("cursor 이전에 발신자 메시지가 없으면 빈 리스트를 반환한다")
    void findByRoomAndMemberBefore_cursor_이전_메시지_없음() {
        List<Message> result = adaptor.findByRoomAndMemberBefore(roomId, sender1, msg1Id, 10);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("limit이 0 이하면 예외를 던진다")
    void findByRoomAndMemberBefore_limit_0이하_예외() {
        assertThatThrownBy(() -> adaptor.findByRoomAndMemberBefore(roomId, sender1, null, 0))
                .isInstanceOf(InvalidDataAccessApiUsageException.class)
                .hasMessageContaining("limit must be positive");
    }

    // ──────────────────────── countAfter ────────────────────────

    @Test
    @DisplayName("afterMessageId가 null이면 전체 ACTIVE 메시지 수를 반환한다 (countAll 경로)")
    void countAfter_null이면_전체_카운트() {
        long count = adaptor.countAfter(roomId, null);

        assertThat(count).isEqualTo(3);
    }

    @Test
    @DisplayName("afterMessageId가 있으면 해당 id 이후 ACTIVE 메시지 수를 반환한다 (countAfter 경로)")
    void countAfter_cursor_있으면_이후_카운트() {
        long count = adaptor.countAfter(roomId, msg1Id);

        assertThat(count).isEqualTo(2);
    }

    // ──────────────────────── countUnreadByRooms ────────────────────────

    @Test
    @DisplayName("빈 맵이면 DB 조회 없이 빈 맵을 반환한다")
    void countUnreadByRooms_빈_맵_즉시_반환() {
        Map<ChatRoomId, Long> result = adaptor.countUnreadByRooms(Map.of());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("lastReadId가 null이면 0으로 취급해 전체 메시지를 미읽음으로 계산한다")
    void countUnreadByRooms_lastReadId_null이면_전체_미읽음() {
        Map<ChatRoomId, Long> lastReadMap = new java.util.HashMap<>();
        lastReadMap.put(roomId, null);

        Map<ChatRoomId, Long> result = adaptor.countUnreadByRooms(lastReadMap);

        assertThat(result.get(roomId)).isEqualTo(3);
    }

    // ──────────────────────── 픽스처 헬퍼 ────────────────────────

    private MessageJpaEntity message(Long roomId, Long senderId, String content) {
        return new MessageJpaEntity(null, roomId, senderId, content, "TEXT", "ACTIVE", INSTANT_NOW);
    }
}
