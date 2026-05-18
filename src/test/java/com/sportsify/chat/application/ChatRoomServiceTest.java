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

    /**
     * GAME 방 생성에 gameId가 없으면 DB 저장 전 단계에서 예외를 던져야 한다.
     * gameId null 허용 시 데이터 무결성 위반이 런타임에서야 발각되는 문제를 사전 차단.
     * Mock: 저장 레이어에 도달하지 않으므로 별도 stub 불필요
     */
    @Test
    @DisplayName("GAME 타입 생성 시 gameId가 없으면 예외가 발생한다")
    void create_GAME타입_gameId없음_예외() {
        assertThatThrownBy(() -> chatRoomService.create(
                new CreateChatRoomRequest("GAME", "경기방", null, null, List.of(INVITEE_ID)), MEMBER_ID))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * GAME 방은 이름이 필수다. 이름 없이 저장되면 STOMP 클라이언트가 방 목록을 정상 렌더링할 수 없음.
     */
    @Test
    @DisplayName("GAME 타입 생성 시 이름이 없으면 예외가 발생한다")
    void create_GAME타입_이름없음_예외() {
        assertThatThrownBy(() -> chatRoomService.create(
                new CreateChatRoomRequest("GAME", null, null, 5L, List.of(INVITEE_ID)), MEMBER_ID))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * DIRECT 방은 1:1이므로 invitee가 2명이면 즉시 거부해야 한다.
     * 어드바이저리 락을 걸기 전 검증하므로 DB 부하 없이 빠르게 실패.
     */
    @Test
    @DisplayName("DIRECT 타입 생성 시 초대 인원이 2명이면 예외가 발생한다")
    void create_DIRECT_초대인원2명_예외() {
        assertThatThrownBy(() -> chatRoomService.create(
                new CreateChatRoomRequest("DIRECT", null, null, null, List.of(INVITEE_ID, 3L)), MEMBER_ID))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * 자기 자신과의 DM은 의미가 없으며 방 목록에 혼란을 줄 수 있다.
     * 락 획득 전 검증이므로 Mock: lock/repo 불필요
     */
    @Test
    @DisplayName("DIRECT 타입 생성 시 자기 자신을 초대하면 예외가 발생한다")
    void create_DIRECT_자기자신초대_예외() {
        assertThatThrownBy(() -> chatRoomService.create(
                new CreateChatRoomRequest("DIRECT", null, null, null, List.of(MEMBER_ID)), MEMBER_ID))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * 같은 두 사용자 간 DM이 동시에 생성되는 race condition을 어드바이저리 락으로 막는다.
     * lock 실패 → CONFLICT 예외. DB 조회 없이 락 단계에서 차단.
     * Mock: tryAcquireXactLock → false (락 선점 불가 시뮬레이션)
     */
    @Test
    @DisplayName("DIRECT 타입 생성 시 어드바이저리 락 획득에 실패하면 예외가 발생한다")
    void create_DIRECT_lock획득실패_예외() {
        given(advisoryLockAdaptor.tryAcquireXactLock(any())).willReturn(false);

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

    /**
     * 이름·이미지 둘 다 null이면 변경할 내용이 없는 요청이므로 빠르게 거부.
     * DB 조회 이전에 검증되어야 불필요한 락 획득을 방지한다.
     */
    @Test
    @DisplayName("이름과 이미지가 모두 null인 수정 요청은 예외가 발생한다")
    void update_빈요청_예외() {
        assertThatThrownBy(() -> chatRoomService.update(
                new ChatRoomUpdateRequest(null, null), ROOM_ID, MEMBER_ID))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * 방장 외에는 방 정보를 수정할 수 없다.
     * createdBy(MEMBER_ID)와 요청자(INVITEE_ID)가 다를 때 FORBIDDEN 예외 발생 여부 검증.
     * Mock: findById는 BeforeEach stub 재사용
     */
    @Test
    @DisplayName("방장이 아닌 멤버가 채팅방을 수정하면 예외가 발생한다")
    void update_권한없음_예외() {
        assertThatThrownBy(() -> chatRoomService.update(
                new ChatRoomUpdateRequest("새이름", null), ROOM_ID, INVITEE_ID))
                .isInstanceOf(BusinessException.class);
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

    /**
     * findActiveRoomForUpdate는 ACTIVE/EMPTY 상태만 허용한다.
     * ARCHIVED → ARCHIVED 이중 전이를 방지하는 상태 머신 보호 검증.
     * Mock: findByIdForUpdateWrite → ARCHIVED 방 반환
     */
    @Test
    @DisplayName("ARCHIVED 상태 방을 다시 archive하면 예외가 발생한다")
    void archive_이미아카이브됨_예외() {
        ChatRoom archived = chatRoom(ROOM_ID, "한화 VS LG", ChatRoomType.GAME, 5L, MEMBER_ID, ChatRoomStatus.ARCHIVED);
        given(chatRoomRepo.findByIdForUpdateWrite(ChatRoomId.of(ROOM_ID))).willReturn(Optional.of(archived));

        assertThatThrownBy(() -> chatRoomService.archive(ROOM_ID, MEMBER_ID))
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

    /**
     * unarchive도 방장 전용 작업이다.
     * Mock: findByIdForUpdateWrite → ARCHIVED 방 (방장=MEMBER_ID), 요청자=INVITEE_ID
     */
    @Test
    @DisplayName("방장이 아닌 멤버가 채팅방을 unarchive하면 예외가 발생한다")
    void unarchive_권한없음_예외() {
        ChatRoom archived = chatRoom(ROOM_ID, "한화 VS LG", ChatRoomType.GAME, 5L, MEMBER_ID, ChatRoomStatus.ARCHIVED);
        given(chatRoomRepo.findByIdForUpdateWrite(ChatRoomId.of(ROOM_ID))).willReturn(Optional.of(archived));

        assertThatThrownBy(() -> chatRoomService.unarchive(ROOM_ID, INVITEE_ID))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * DELETED 방은 findNonDeletedRoomForUpdate에서 NOT_FOUND로 차단된다.
     * 삭제된 방을 복원하는 시나리오는 불가여야 한다.
     */
    @Test
    @DisplayName("DELETED 상태 방을 unarchive하면 예외가 발생한다")
    void unarchive_삭제된방_예외() {
        ChatRoom deleted = chatRoom(ROOM_ID, "한화 VS LG", ChatRoomType.GAME, 5L, MEMBER_ID, ChatRoomStatus.DELETED);
        given(chatRoomRepo.findByIdForUpdateWrite(ChatRoomId.of(ROOM_ID))).willReturn(Optional.of(deleted));

        assertThatThrownBy(() -> chatRoomService.unarchive(ROOM_ID, MEMBER_ID))
                .isInstanceOf(BusinessException.class);
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

    /**
     * 방 삭제는 방장 전용이다. 일반 멤버의 삭제 시도를 차단하는지 검증.
     * Mock: findByIdForUpdateWrite → ACTIVE 방 (BeforeEach stub 재사용)
     */
    @Test
    @DisplayName("방장이 아닌 멤버가 채팅방을 삭제하면 예외가 발생한다")
    void delete_권한없음_예외() {
        assertThatThrownBy(() -> chatRoomService.delete(ROOM_ID, INVITEE_ID))
                .isInstanceOf(BusinessException.class);
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

    /**
     * limit보다 방이 많으면 hasNext=true, nextCursor에 마지막 방 ID가 담겨야 한다.
     * 서비스는 limit+1개를 조회해 초과분 존재 여부로 다음 페이지를 판별한다.
     * 실패 포인트: subList 범위 오류 시 응답에 limit+1번째 방이 포함되거나 nextCursor가 null이 됨.
     */
    @Test
    @DisplayName("다음 페이지가 있으면 hasNext가 true이고 nextCursor가 반환된다")
    void getMyRooms_hasNext_true() {
        ChatRoom room1 = chatRoom(10L, "방1", ChatRoomType.GAME, 5L, MEMBER_ID);
        ChatRoom room2 = chatRoom(11L, "방2", ChatRoomType.GAME, 5L, MEMBER_ID);
        ChatRoomId roomId1 = room1.getId();
        ChatRoomId roomId2 = room2.getId();
        MemberId memberId = MemberId.of(MEMBER_ID);

        given(chatRoomMemberRepo.findActiveByMember(memberId)).willReturn(List.of(
                ChatRoomMember.newJoin(roomId1, memberId, NOW),
                ChatRoomMember.newJoin(roomId2, memberId, NOW)
        ));
        // limit=1이므로 서비스는 limit+1=2개를 요청 → 2개 반환 시 hasNext=true
        given(chatRoomRepo.findActiveByRoomIds(any(), eq(ChatRoomType.GAME), isNull(), anyInt()))
                .willReturn(List.of(room1, room2));
        given(messageRepo.findLatestByRooms(any())).willReturn(List.of());
        given(messageRepo.countUnreadByRooms(any())).willReturn(Map.of());
        given(chatRoomMemberRepo.countActiveByRooms(any())).willReturn(Map.of(roomId1, 2L));

        ChatRoomListResponse result = chatRoomService.getMyRooms(new ChatRoomGetRequest("GAME", null, 1), MEMBER_ID);

        assertThat(result.hasNext()).isTrue();
        assertThat(result.nextCursor()).isEqualTo(10L);
        assertThat(result.items()).hasSize(1);
        assertThat(((ChatRoomSummaryResponse) result.items().get(0)).roomId()).isEqualTo(10L);
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

    /**
     * DIRECT 방은 익명 접근이 불가하다. memberId=null로 조회하면 FORBIDDEN 예외가 발생해야 한다.
     * GAME 방과 달리 DIRECT 방은 멤버십 확인이 필요한 비즈니스 규칙.
     * Mock: findById → DIRECT 방 반환, 이후 예외 발생으로 repo 추가 호출 없음
     */
    @Test
    @DisplayName("DIRECT 채팅방 상세 조회 시 memberId가 null이면 예외가 발생한다")
    void getRoomDetail_DIRECT방_익명접근_예외() {
        ChatRoom directRoom = chatRoom(ROOM_ID, "DM", ChatRoomType.DIRECT, null, MEMBER_ID);
        given(chatRoomRepo.findById(ChatRoomId.of(ROOM_ID))).willReturn(Optional.of(directRoom));

        assertThatThrownBy(() -> chatRoomService.getRoomDetail(ROOM_ID, null))
                .isInstanceOf(BusinessException.class);
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
