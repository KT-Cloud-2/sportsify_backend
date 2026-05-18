package com.sportsify.chat.application;

import com.sportsify.chat.application.chatRoom.dto.*;
import com.sportsify.chat.application.chatRoom.service.ChatRoomService;
import com.sportsify.chat.domain.model.chatRoom.*;
import com.sportsify.chat.domain.model.chatRoomMember.ChatRoomMember;
import com.sportsify.chat.domain.repository.ChatRoomMemberRepository;
import com.sportsify.chat.domain.repository.ChatRoomRepository;
import com.sportsify.chat.domain.repository.MessageRepository;
import com.sportsify.chat.infrastructure.persistence.lock.AdvisoryLockAdaptor;
import com.sportsify.common.exception.BusinessException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    private Long ROOM_ID;
    private Long MEMBER_ID;
    private Long INVITEE_ID;
    private ChatRoom chatRoom;

    @BeforeEach
    void setUp() {
        lenient().when(clock.instant()).thenReturn(FIXED);
        lenient().when(clock.getZone()).thenReturn(ZoneOffset.UTC);

        MEMBER_ID = 1L;
        INVITEE_ID = 2L;
        chatRoom = chatRoom(10L, "한화 VS LG", ChatRoomType.GAME, 5L, MEMBER_ID);
        ROOM_ID = chatRoom.getId().value();

        lenient().when(chatRoomRepo.findById(ChatRoomId.of(ROOM_ID))).thenReturn(Optional.of(chatRoom));
        lenient().when(chatRoomRepo.findByIdForUpdateWrite(ChatRoomId.of(ROOM_ID))).thenReturn(Optional.of(chatRoom));
    }

    // ──────────────────────── create ────────────────────────

    @Test
    @DisplayName("GAME 타입 채팅방을 생성한다")
    void create_GAME타입() {
        given(chatRoomRepo.save(any())).willReturn(chatRoom(10L, "한화 VS LG", ChatRoomType.GAME, 5L, MEMBER_ID));
        given(chatRoomMemberRepo.saveAll(any())).willReturn(List.of());

        ChatRoomResponse result = chatRoomService.create(
                new CreateChatRoomRequest("GAME", "한화 VS LG", null, 5L, List.of(INVITEE_ID)), MEMBER_ID);

        assertThat(result.roomId()).isEqualTo(10L);
        assertThat(result.type()).isEqualTo("GAME");
        assertThat(result.name()).isEqualTo("한화 VS LG");
    }

    @Test
    @DisplayName("DIRECT 타입 채팅방을 생성한다")
    void create_DIRECT타입() {
        given(advisoryLockAdaptor.tryAcquireXactLock(any())).willReturn(true);
        given(chatRoomRepo.existByCreatorIdAndInviteId(MEMBER_ID, INVITEE_ID)).willReturn(Optional.empty());
        given(chatRoomRepo.save(any())).willReturn(chatRoom(11L, "DM", ChatRoomType.DIRECT, null, MEMBER_ID));
        given(chatRoomMemberRepo.saveAll(any())).willReturn(List.of());

        ChatRoomResponse result = chatRoomService.create(
                new CreateChatRoomRequest("DIRECT", null, null, null, List.of(INVITEE_ID)), MEMBER_ID);

        assertThat(result.roomId()).isEqualTo(11L);
        assertThat(result.type()).isEqualTo("DIRECT");
    }

    @Test
    @DisplayName("이미 존재하는 DIRECT 채팅방을 중복 생성하면 예외가 발생한다")
    void create_DIRECT_중복_예외() {
        given(advisoryLockAdaptor.tryAcquireXactLock(any())).willReturn(true);
        given(chatRoomRepo.existByCreatorIdAndInviteId(MEMBER_ID, INVITEE_ID)).willReturn(Optional.of(11L));

        assertThatThrownBy(() -> chatRoomService.create(
                new CreateChatRoomRequest("DIRECT", null, null, null, List.of(INVITEE_ID)), MEMBER_ID))
                .isInstanceOf(BusinessException.class);
    }

    // ──────────────────────── update ────────────────────────

    @Test
    @DisplayName("채팅방 이름과 이미지를 수정한다")
    void update_수정성공() {
        given(chatRoomRepo.findById(ChatRoomId.of(ROOM_ID))).willReturn(Optional.of(chatRoom));
        given(chatRoomRepo.save(any())).willAnswer(inv -> inv.getArgument(0));

        ChatRoomUpdateResponse result = chatRoomService.update(
                new ChatRoomUpdateRequest("새로운 채팅방", "https://new.png"), ROOM_ID, MEMBER_ID);

        assertThat(result.roomId()).isEqualTo(ROOM_ID);
        assertThat(result.name()).isEqualTo("새로운 채팅방");
        assertThat(result.imageUrl()).isEqualTo("https://new.png");
    }

    // ──────────────────────── archive ────────────────────────

    @Test
    @DisplayName("방장이 채팅방을 아카이브한다")
    void archive_성공() {
        given(chatRoomRepo.findByIdForUpdateWrite(ChatRoomId.of(ROOM_ID))).willReturn(Optional.of(chatRoom));
        given(chatRoomRepo.save(any())).willAnswer(inv -> inv.getArgument(0));

        ChatRoomArchiveResponse result = chatRoomService.archive(ROOM_ID, MEMBER_ID);

        assertThat(result.roomId()).isEqualTo(ROOM_ID);
        assertThat(result.status()).isEqualTo("ARCHIVED");
    }

    @Test
    @DisplayName("방장이 아닌 멤버가 아카이브하면 예외가 발생한다")
    void archive_권한없음_예외() {
        given(chatRoomRepo.findByIdForUpdateWrite(ChatRoomId.of(ROOM_ID))).willReturn(Optional.of(chatRoom));

        assertThatThrownBy(() -> chatRoomService.archive(ROOM_ID, INVITEE_ID))
                .isInstanceOf(BusinessException.class);
    }

    // ──────────────────────── unarchive ────────────────────────

    @Test
    @DisplayName("방장이 아카이브된 채팅방을 복원한다")
    void unarchive_성공() {
        ChatRoom archived = chatRoom(ROOM_ID, "한화 VS LG", ChatRoomType.GAME, 5L, MEMBER_ID, ChatRoomStatus.ARCHIVED);
        given(chatRoomRepo.findByIdForUpdateWrite(ChatRoomId.of(ROOM_ID))).willReturn(Optional.of(archived));
        given(chatRoomRepo.save(any())).willAnswer(inv -> inv.getArgument(0));

        ChatRoomArchiveResponse result = chatRoomService.unarchive(ROOM_ID, MEMBER_ID);

        assertThat(result.roomId()).isEqualTo(ROOM_ID);
        assertThat(result.status()).isEqualTo("ACTIVE");
    }

    // ──────────────────────── delete ────────────────────────

    @Test
    @DisplayName("채팅방을 삭제하고 멤버를 전원 퇴장시킨다")
    void delete_삭제성공() {
        given(chatRoomRepo.findByIdForUpdateWrite(ChatRoomId.of(ROOM_ID))).willReturn(Optional.of(chatRoom));
        given(chatRoomRepo.save(any())).willReturn(chatRoom);

        chatRoomService.delete(ROOM_ID, MEMBER_ID);

        assertThat(chatRoom.getStatus()).isEqualTo(ChatRoomStatus.DELETED);
        verify(chatRoomMemberRepo).leaveAllMembersByRoom(eq(ChatRoomId.of(ROOM_ID)), any());
    }

    // ──────────────────────── getMyRooms ────────────────────────

    @Test
    @DisplayName("내 채팅방 목록을 반환한다")
    void getMyRooms_목록반환() {
        ChatRoomId roomId = ChatRoomId.of(ROOM_ID);
        MemberId memberId = MemberId.of(MEMBER_ID);
        ChatRoomMember membership = ChatRoomMember.newJoin(roomId, memberId, NOW);

        given(chatRoomMemberRepo.findActiveByMember(memberId)).willReturn(List.of(membership));
        given(chatRoomRepo.findActiveByRoomIds(any(), eq(ChatRoomType.GAME), isNull(), anyInt())).willReturn(List.of(chatRoom));
        given(messageRepo.findLatestByRooms(any())).willReturn(List.of());
        given(messageRepo.countUnreadByRooms(any())).willReturn(Map.of());
        given(chatRoomMemberRepo.countActiveByRooms(any())).willReturn(Map.of(roomId, 3L));

        ChatRoomListResponse result = chatRoomService.getMyRooms(new ChatRoomGetRequest("GAME", null, 20), MEMBER_ID);

        assertThat(result.totalCount()).isEqualTo(1);
        assertThat(result.hasNext()).isFalse();
        assertThat(result.items()).hasSize(1);
    }

    @Test
    @DisplayName("참여 중인 채팅방이 없으면 빈 목록을 반환한다")
    void getMyRooms_빈목록() {
        given(chatRoomMemberRepo.findActiveByMember(any())).willReturn(List.of());

        ChatRoomListResponse result = chatRoomService.getMyRooms(new ChatRoomGetRequest("GAME", null, 20), MEMBER_ID);

        assertThat(result.items()).isEmpty();
        assertThat(result.hasNext()).isFalse();
        assertThat(result.totalCount()).isZero();
    }

    // ──────────────────────── getRoomsByGameId ────────────────────────

    @Test
    @DisplayName("게임별 채팅방 목록을 반환한다")
    void getRoomsByGameId_목록반환() {
        ChatRoomId roomId = ChatRoomId.of(ROOM_ID);
        given(chatRoomRepo.findActiveByGameId(eq(GameId.of(5L)), isNull(), anyInt())).willReturn(List.of(chatRoom));
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
        MemberId memberId = MemberId.of(MEMBER_ID);
        ChatRoomMember membership = ChatRoomMember.newJoin(ChatRoomId.of(ROOM_ID), memberId, NOW);

        given(chatRoomRepo.findById(ChatRoomId.of(ROOM_ID))).willReturn(Optional.of(chatRoom));
        given(chatRoomMemberRepo.countActiveByRoom(ChatRoomId.of(ROOM_ID))).willReturn(5L);
        given(chatRoomMemberRepo.findByRoomAndMember(ChatRoomId.of(ROOM_ID), memberId)).willReturn(Optional.of(membership));

        ChatRoomDetailResponse result = chatRoomService.getRoomDetail(ROOM_ID, MEMBER_ID);

        assertThat(result.roomId()).isEqualTo(ROOM_ID);
        assertThat(result.currentParticipants()).isEqualTo(5L);
        assertThat(result.myMembership()).isPresent();
    }

    // ──────────────────────── 픽스처 헬퍼 ────────────────────────

    private ChatRoom chatRoom(Long id, String name, ChatRoomType type, Long gameId, Long createdBy) {
        return chatRoom(id, name, type, gameId, createdBy, ChatRoomStatus.ACTIVE);
    }

    private ChatRoom chatRoom(Long id, String name, ChatRoomType type, Long gameId, Long createdBy, ChatRoomStatus status) {
        return ChatRoom.restore(
                ChatRoomId.of(id), ChatRoomName.of(name), type, null,
                gameId != null ? GameId.of(gameId) : null,
                NOW, NOW, status, MemberId.of(createdBy)
        );
    }
}
