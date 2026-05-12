package com.sportsify.chat.application;

import com.sportsify.chat.application.chatRoom.dto.*;
import com.sportsify.chat.application.chatRoom.service.ChatRoomService;
import com.sportsify.chat.domain.model.chatRoom.*;
import com.sportsify.chat.domain.model.chatRoomMember.ChatRoomMember;
import com.sportsify.chat.domain.repository.ChatRoomMemberRepository;
import com.sportsify.chat.domain.repository.ChatRoomRepository;
import com.sportsify.chat.domain.repository.MessageRepository;
import com.sportsify.chat.infrastructure.persistence.lock.AdvisoryLockAdaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatRoomServiceTest {

    private static final Instant FIXED = Instant.parse("2026-05-04T12:00:00Z");
    private static final LocalDateTime NOW = LocalDateTime.ofInstant(FIXED, ZoneOffset.UTC);
    @InjectMocks
    private ChatRoomService chatRoomService;
    @Mock
    private ChatRoomRepository chatRoomRepo;
    @Mock
    private ChatRoomMemberRepository chatRoomMemberRepo;
    @Mock
    private Clock clock;
    @Mock
    private AdvisoryLockAdaptor advisoryLockAdaptor;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private MessageRepository messageRepo;

    @BeforeEach
    void setUp() {
        lenient().when(clock.instant()).thenReturn(FIXED);
        lenient().when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    // ──────────────────────── create ────────────────────────

    @Test
    @DisplayName("GAME 타입 채팅방을 생성한다")
    void create_GAME타입() {
        given(chatRoomRepo.save(any())).willReturn(chatRoom(10L, "한화 VS LG", ChatRoomType.GAME, 5L, 1L));
        given(chatRoomMemberRepo.saveAll(any())).willReturn(List.of());

        ChatRoomResponse result = chatRoomService.create(
                new CreateChatRoomRequest("GAME", "한화 VS LG", null, 5L, List.of(2L)), 1L);

        assertThat(result.roomId()).isEqualTo(10L);
        assertThat(result.type()).isEqualTo("GAME");
        assertThat(result.name()).isEqualTo("한화 VS LG");
    }

    @Test
    @DisplayName("DIRECT 타입 채팅방을 생성한다")
    void create_DIRECT타입() {
        given(advisoryLockAdaptor.tryAcquireXactLock(any())).willReturn(true);
        given(chatRoomRepo.existByCreatorIdAndInviteId(1L, 2L)).willReturn(Optional.empty());
        given(chatRoomRepo.save(any())).willReturn(chatRoom(11L, "DM", ChatRoomType.DIRECT, null, 1L));
        given(chatRoomMemberRepo.saveAll(any())).willReturn(List.of());

        ChatRoomResponse result = chatRoomService.create(
                new CreateChatRoomRequest("DIRECT", null, null, null, List.of(2L)), 1L);

        assertThat(result.roomId()).isEqualTo(11L);
        assertThat(result.type()).isEqualTo("DIRECT");
    }

    // ──────────────────────── update ────────────────────────

    @Test
    @DisplayName("채팅방 이름과 이미지를 수정한다")
    void update_수정성공() {
        ChatRoom room = chatRoom(10L, "한화 VS LG", ChatRoomType.GAME, 5L, 1L);
        given(chatRoomRepo.findById(ChatRoomId.of(10L))).willReturn(Optional.of(room));
        given(chatRoomRepo.save(any())).willAnswer(inv -> inv.getArgument(0));

        ChatRoomUpdateResponse result = chatRoomService.update(
                new ChatRoomUpdateRequest("새로운 채팅방", "https://new.png"), 10L, 1L);

        assertThat(result.roomId()).isEqualTo(10L);
        assertThat(result.name()).isEqualTo("새로운 채팅방");
        assertThat(result.imageUrl()).isEqualTo("https://new.png");
    }

    // ──────────────────────── delete ────────────────────────

    @Test
    @DisplayName("채팅방을 삭제하고 멤버를 전원 퇴장시킨다")
    void delete_삭제성공() {
        ChatRoom room = chatRoom(10L, "한화 VS LG", ChatRoomType.GAME, 5L, 1L);
        given(chatRoomRepo.findByIdForUpdateWrite(ChatRoomId.of(10L))).willReturn(Optional.of(room));
        given(chatRoomRepo.save(any())).willReturn(room);

        chatRoomService.delete(10L, 1L);

        assertThat(room.getStatus()).isEqualTo(ChatRoomStatus.DELETED);
        verify(chatRoomMemberRepo).leaveAllMembersByRoom(eq(ChatRoomId.of(10L)), any());
    }

    // ──────────────────────── getMyRooms ────────────────────────

    @Test
    @DisplayName("내 채팅방 목록을 반환한다")
    void getMyRooms_목록반환() {
        ChatRoomId roomId = ChatRoomId.of(10L);
        MemberId memberId = MemberId.of(1L);
        ChatRoom room = chatRoom(10L, "한화 VS LG", ChatRoomType.GAME, 5L, 1L);
        ChatRoomMember membership = ChatRoomMember.newJoin(roomId, memberId, NOW);

        given(chatRoomMemberRepo.findActiveByMember(memberId)).willReturn(List.of(membership));
        given(chatRoomRepo.findActiveByRoomIds(any(), eq(ChatRoomType.GAME), isNull(), anyInt())).willReturn(List.of(room));
        given(messageRepo.findMyLatestByRooms(any(), any())).willReturn(List.of());
        given(chatRoomMemberRepo.countActiveByRooms(any())).willReturn(Map.of(roomId, 3L));

        ChatRoomListResponse result = chatRoomService.getMyRooms(new ChatRoomGetRequest("GAME", null, 20), 1L);

        assertThat(result.totalCount()).isEqualTo(1);
        assertThat(result.hasNext()).isFalse();
        assertThat(result.items()).hasSize(1);
    }

    // ──────────────────────── getRoomsByGameId ────────────────────────

    @Test
    @DisplayName("게임별 채팅방 목록을 반환한다")
    void getRoomsByGameId_목록반환() {
        ChatRoomId roomId = ChatRoomId.of(10L);
        ChatRoom room = chatRoom(10L, "한화 VS LG", ChatRoomType.GAME, 5L, 1L);

        given(chatRoomRepo.findActiveByGameId(eq(GameId.of(5L)), isNull(), anyInt())).willReturn(List.of(room));
        given(chatRoomMemberRepo.countActiveByRooms(any())).willReturn(Map.of(roomId, 3L));

        ChatRoomListResponse result = chatRoomService.getRoomsByGameId(new ChatRoomGetByGameRequest(null, 20), 5L);

        assertThat(result.totalCount()).isEqualTo(1);
        assertThat(result.hasNext()).isFalse();
        assertThat(result.items()).hasSize(1);
    }

    // ──────────────────────── getRoomDetail ────────────────────────

    @Test
    @DisplayName("채팅방 상세 정보를 반환한다")
    void getRoomDetail_상세반환() {
        ChatRoomId roomId = ChatRoomId.of(10L);
        MemberId memberId = MemberId.of(1L);
        ChatRoom room = chatRoom(10L, "한화 VS LG", ChatRoomType.GAME, 5L, 1L);
        ChatRoomMember membership = ChatRoomMember.newJoin(roomId, memberId, NOW);

        given(chatRoomRepo.findById(roomId)).willReturn(Optional.of(room));
        given(chatRoomMemberRepo.countActiveByRoom(roomId)).willReturn(5L);
        given(chatRoomMemberRepo.findByRoomAndMember(roomId, memberId)).willReturn(Optional.of(membership));

        ChatRoomDetailResponse result = chatRoomService.getRoomDetail(10L, 1L);

        assertThat(result.roomId()).isEqualTo(10L);
        assertThat(result.currentParticipants()).isEqualTo(5L);
        assertThat(result.myMembership()).isPresent();
    }

    // ──────────────────────── 픽스처 헬퍼 ────────────────────────

    private ChatRoom chatRoom(Long id, String name, ChatRoomType type, Long gameId, Long createdBy) {
        return ChatRoom.restore(
                ChatRoomId.of(id), ChatRoomName.of(name), type, null,
                gameId != null ? GameId.of(gameId) : null,
                NOW, NOW, ChatRoomStatus.ACTIVE, MemberId.of(createdBy)
        );
    }
}
