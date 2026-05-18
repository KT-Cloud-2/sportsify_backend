package com.sportsify.chat.application;

import com.sportsify.chat.application.chatRoomMember.dto.ChatRoomMemberResponse;
import com.sportsify.chat.application.chatRoomMember.service.ChatRoomMemberService;
import com.sportsify.chat.domain.model.chatRoom.*;
import com.sportsify.chat.domain.model.chatRoomMember.ChatRoomMember;
import com.sportsify.chat.domain.model.chatRoomMember.MemberStatus;
import com.sportsify.chat.domain.repository.ChatRoomMemberRepository;
import com.sportsify.chat.domain.repository.ChatRoomRepository;
import com.sportsify.chat.infrastructure.persistence.lock.AdvisoryLockAdaptor;
import com.sportsify.common.exception.BusinessException;
import com.sportsify.member.domain.model.Member;
import com.sportsify.member.domain.model.OAuthProvider;
import com.sportsify.member.domain.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatRoomMemberServiceTest {

    // 테스트에서 공통으로 사용할 상수 및 변수
    private static final Instant FIXED_INSTANT = Instant.parse("2026-05-04T12:00:00Z");
    private static final LocalDateTime NOW = LocalDateTime.ofInstant(FIXED_INSTANT, ZoneOffset.UTC);
    @InjectMocks
    private ChatRoomMemberService chatRoomMemberService;
    @Mock
    private ChatRoomRepository chatRoomRepo;
    @Mock
    private ChatRoomMemberRepository chatRoomMemberRepo;
    @Mock
    private MemberRepository memberRepo;
    @Mock
    private Clock clock;
    @Mock
    private AdvisoryLockAdaptor advisoryLockAdaptor;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private Long ROOM_ID;
    private Long MEMBER_ID;
    private Long INVITEE_ID;

    private ChatRoom chatRoom;
    private Member member;
    private Member invitee;

    @BeforeEach
    void setUp() {
        lenient().when(clock.instant()).thenReturn(FIXED_INSTANT);
        lenient().when(clock.getZone()).thenReturn(ZoneOffset.UTC);

        member = createMember(1L, "주최자", "g-s1");
        invitee = createMember(2L, "초대대상", "g-s2");
        MEMBER_ID = member.getId();
        INVITEE_ID = invitee.getId();
        chatRoom = createChatRoom(1L, MEMBER_ID, "테스트 채팅방");

        ROOM_ID = chatRoom.getId().value();

        lenient().when(memberRepo.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        lenient().when(memberRepo.findById(INVITEE_ID)).thenReturn(Optional.of(invitee));
        lenient().when(chatRoomRepo.findById(ChatRoomId.of(ROOM_ID))).thenReturn(Optional.of(chatRoom));
    }

    private Member createMember(Long id, String name, String providerId) {
        Member member = Member.create("s1@test.com", name, OAuthProvider.GOOGLE, providerId);
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private ChatRoom createChatRoom(Long id, Long creatorId, String title) {
        ChatRoom room = ChatRoom.create(ChatRoomName.of(title), ChatRoomType.DIRECT, null, null, MemberId.of(creatorId), NOW);
        ReflectionTestUtils.setField(room, "id", ChatRoomId.of(id));
        return room;
    }

    // ──────────────────────── join ────────────────────────

    @Test
    @DisplayName("신규 멤버가 GAME 채팅방에 입장한다")
    void join_신규멤버_GAME방_입장() {
        given(chatRoomRepo.findById(ChatRoomId.of(ROOM_ID))).willReturn(Optional.of(gameRoom()));
        given(advisoryLockAdaptor.tryAcquireXactLock(any())).willReturn(true);
        given(chatRoomMemberRepo.findByRoomAndMemberWithStatus(any(), any(), anyList())).willReturn(Optional.empty());
        given(chatRoomMemberRepo.saveAndFlush(any())).willAnswer(inv -> inv.getArgument(0));

        ChatRoomMemberResponse result = chatRoomMemberService.join(ROOM_ID, MEMBER_ID);

        assertThat(result.roomId()).isEqualTo(ROOM_ID);
        assertThat(result.memberId()).isEqualTo(MEMBER_ID);
        assertThat(result.status()).isEqualTo("JOINED");
    }

    @Test
    @DisplayName("INVITED 상태 멤버가 입장을 수락한다")
    void join_초대멤버_수락_입장() {
        given(chatRoomRepo.findById(ChatRoomId.of(ROOM_ID))).willReturn(Optional.of(gameRoom()));
        given(advisoryLockAdaptor.tryAcquireXactLock(any())).willReturn(true);
        given(chatRoomMemberRepo.findByRoomAndMemberWithStatus(any(), any(), anyList()))
                .willReturn(Optional.of(member(MemberStatus.INVITED, MEMBER_ID)));
        given(chatRoomMemberRepo.save(any())).willAnswer(inv -> inv.getArgument(0));

        ChatRoomMemberResponse result = chatRoomMemberService.join(ROOM_ID, MEMBER_ID);

        assertThat(result.roomId()).isEqualTo(ROOM_ID);
        assertThat(result.memberId()).isEqualTo(MEMBER_ID);
        assertThat(result.status()).isEqualTo("JOINED");
    }

    /**
     * ARCHIVED 방은 findChatRoomWithId에서 BUSINESS_RULE_VIOLATION을 던진다.
     * 입장 요청이 락 획득 직후에 방 상태를 검증해야 race condition에서도 안전하다.
     * Mock: findById → ARCHIVED 방 반환
     */
    @Test
    @DisplayName("ARCHIVED 채팅방 입장 시 예외가 발생한다")
    void join_ARCHIVED방_예외() {
        ChatRoom archived = ChatRoom.restore(
                ChatRoomId.of(ROOM_ID), ChatRoomName.of("방"), ChatRoomType.GAME,
                null, GameId.of(5L), NOW, NOW, ChatRoomStatus.ARCHIVED, MemberId.of(MEMBER_ID));
        given(chatRoomRepo.findById(ChatRoomId.of(ROOM_ID))).willReturn(Optional.of(archived));

        assertThatThrownBy(() -> chatRoomMemberService.join(ROOM_ID, MEMBER_ID))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * GAME 방에서 BANNED 멤버의 재입장은 차단된다.
     * BAN은 단방향 영구 제재로 GAME/DIRECT 구분 없이 동일하게 적용된다.
     * Mock: findById → GAME 방, findByRoomAndMemberWithStatus → BANNED 멤버
     */
    @Test
    @DisplayName("BANNED 멤버가 GAME 채팅방 재입장 시도하면 예외가 발생한다")
    void join_BANNED멤버_GAME방_예외() {
        given(chatRoomRepo.findById(ChatRoomId.of(ROOM_ID))).willReturn(Optional.of(gameRoom()));
        given(advisoryLockAdaptor.tryAcquireXactLock(any())).willReturn(true);
        given(chatRoomMemberRepo.findByRoomAndMemberWithStatus(any(), any(), anyList()))
                .willReturn(Optional.of(member(MemberStatus.BANNED, MEMBER_ID)));

        assertThatThrownBy(() -> chatRoomMemberService.join(ROOM_ID, MEMBER_ID))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * DIRECT 방에서 BANNED 멤버의 재입장은 차단된다.
     * DIRECT 방은 초대 기록이 있어야 입장 가능하지만, BANNED 상태이면 초대 수락도 불가.
     * Mock: findById → DIRECT 방(BeforeEach chatRoom), findByRoomAndMemberWithStatus → BANNED 멤버
     */
    @Test
    @DisplayName("BANNED 멤버가 DIRECT 채팅방 재입장 시도하면 예외가 발생한다")
    void join_BANNED멤버_DIRECT방_예외() {
        given(advisoryLockAdaptor.tryAcquireXactLock(any())).willReturn(true);
        given(chatRoomMemberRepo.findByRoomAndMemberWithStatus(any(), any(), anyList()))
                .willReturn(Optional.of(member(MemberStatus.BANNED, MEMBER_ID)));

        assertThatThrownBy(() -> chatRoomMemberService.join(ROOM_ID, MEMBER_ID))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * 이미 JOINED 상태인 멤버의 중복 입장은 허용하지 않는다.
     * 중복 입장 허용 시 멤버 카운트 오류, 이벤트 중복 발행 등 부작용 발생.
     * Mock: findByRoomAndMemberWithStatus → JOINED 멤버 반환
     */
    @Test
    @DisplayName("이미 JOINED 상태인 멤버가 재입장하면 예외가 발생한다")
    void join_이미JOINED_예외() {
        given(chatRoomRepo.findById(ChatRoomId.of(ROOM_ID))).willReturn(Optional.of(gameRoom()));
        given(advisoryLockAdaptor.tryAcquireXactLock(any())).willReturn(true);
        given(chatRoomMemberRepo.findByRoomAndMemberWithStatus(any(), any(), anyList()))
                .willReturn(Optional.of(member(MemberStatus.JOINED, MEMBER_ID)));

        assertThatThrownBy(() -> chatRoomMemberService.join(ROOM_ID, MEMBER_ID))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * DIRECT 방은 명시적 초대 없이는 입장 불가하다.
     * memberOpt.isEmpty() + DIRECT 타입 조합에서 FORBIDDEN 예외가 발생해야 한다.
     * Mock: findByRoomAndMemberWithStatus → Optional.empty (초대 기록 없음)
     */
    @Test
    @DisplayName("DIRECT 채팅방에 초대받지 않은 멤버가 입장하면 예외가 발생한다")
    void join_DIRECT방_초대없는멤버_예외() {
        given(chatRoomRepo.findById(ChatRoomId.of(ROOM_ID))).willReturn(Optional.of(chatRoom));
        given(advisoryLockAdaptor.tryAcquireXactLock(any())).willReturn(true);
        given(chatRoomMemberRepo.findByRoomAndMemberWithStatus(any(), any(), anyList()))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> chatRoomMemberService.join(ROOM_ID, MEMBER_ID))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * 동시 입장 요청 시 어드바이저리 락으로 중복 처리를 방지한다.
     * 락 획득 실패 즉시 CONFLICT 예외를 반환해야 DB 부하가 없다.
     * Mock: tryAcquireXactLock → false (락 선점 불가 시뮬레이션)
     */
    @Test
    @DisplayName("어드바이저리 락 획득에 실패하면 예외가 발생한다")
    void join_lock획득실패_예외() {
        given(chatRoomRepo.findById(ChatRoomId.of(ROOM_ID))).willReturn(Optional.of(gameRoom()));
        given(advisoryLockAdaptor.tryAcquireXactLock(any())).willReturn(false);

        assertThatThrownBy(() -> chatRoomMemberService.join(ROOM_ID, MEMBER_ID))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * EMPTY 상태 방에 멤버가 입장하면 방이 ACTIVE로 재활성화되어야 한다.
     * 방 상태 복구가 누락되면 이후 메시지 전송이 EMPTY 방 상태로 처리되어 오류 발생.
     * 실패 포인트: chatRoomRepo.save가 호출되지 않으면 방 상태가 EMPTY로 남음.
     * Mock: gameRoom EMPTY, chatRoomMemberRepo.saveAndFlush → 신규 멤버
     */
    @Test
    @DisplayName("EMPTY 상태 방에 멤버가 입장하면 방이 ACTIVE로 재활성화된다")
    void join_EMPTY방_재활성화() {
        ChatRoom emptyRoom = ChatRoom.restore(
                ChatRoomId.of(ROOM_ID), ChatRoomName.of("한화 VS LG"), ChatRoomType.GAME,
                null, GameId.of(5L), NOW, NOW, ChatRoomStatus.EMPTY, MemberId.of(MEMBER_ID));
        given(chatRoomRepo.findById(ChatRoomId.of(ROOM_ID))).willReturn(Optional.of(emptyRoom));
        given(advisoryLockAdaptor.tryAcquireXactLock(any())).willReturn(true);
        given(chatRoomMemberRepo.findByRoomAndMemberWithStatus(any(), any(), anyList()))
                .willReturn(Optional.empty());
        given(chatRoomMemberRepo.saveAndFlush(any())).willAnswer(inv -> inv.getArgument(0));

        chatRoomMemberService.join(ROOM_ID, INVITEE_ID);

        ArgumentCaptor<ChatRoom> captor = ArgumentCaptor.forClass(ChatRoom.class);
        verify(chatRoomRepo).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ChatRoomStatus.ACTIVE);
    }

    // ──────────────────────── leave ────────────────────────

    @Test
    @DisplayName("JOINED 멤버가 채팅방을 퇴장한다")
    void leave_퇴장() {
        given(chatRoomMemberRepo.findByRoomAndMemberWithStatus(any(), any(), anyList()))
                .willReturn(Optional.of(member(MemberStatus.JOINED, MEMBER_ID)));
        given(chatRoomMemberRepo.saveAndFlush(any())).willAnswer(inv -> inv.getArgument(0));
        given(chatRoomMemberRepo.countActiveByRoom(any())).willReturn(1L);

        ChatRoomMemberResponse result = chatRoomMemberService.leave(ROOM_ID, MEMBER_ID);

        assertThat(result.status()).isEqualTo("LEFT");
    }

    /**
     * ARCHIVED 방에서는 퇴장 요청도 거부해야 한다.
     * 아카이브 기간 중 멤버 상태 변경을 방지하여 방 이력 무결성 보호.
     * Mock: findById → ARCHIVED 방 반환
     */
    @Test
    @DisplayName("ARCHIVED 채팅방에서 퇴장 시 예외가 발생한다")
    void leave_ARCHIVED방_예외() {
        ChatRoom archived = ChatRoom.restore(
                ChatRoomId.of(ROOM_ID), ChatRoomName.of("방"), ChatRoomType.DIRECT,
                null, null, NOW, NOW, ChatRoomStatus.ARCHIVED, MemberId.of(MEMBER_ID));
        given(chatRoomRepo.findById(ChatRoomId.of(ROOM_ID))).willReturn(Optional.of(archived));

        assertThatThrownBy(() -> chatRoomMemberService.leave(ROOM_ID, MEMBER_ID))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * 이미 LEFT 상태인 멤버의 중복 퇴장은 CONFLICT 예외를 던져야 한다.
     * 이중 퇴장 허용 시 멤버 카운트 오류 및 이벤트 중복 발행 위험.
     * Mock: findByRoomAndMemberWithStatus → LEFT 멤버 반환
     */
    @Test
    @DisplayName("이미 퇴장한 멤버가 다시 퇴장하면 예외가 발생한다")
    void leave_이미LEFT_예외() {
        given(chatRoomMemberRepo.findByRoomAndMemberWithStatus(any(), any(), anyList()))
                .willReturn(Optional.of(member(MemberStatus.LEFT, MEMBER_ID)));

        assertThatThrownBy(() -> chatRoomMemberService.leave(ROOM_ID, MEMBER_ID))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * BANNED 멤버는 퇴장 API를 통해 상태를 LEFT로 변경할 수 없다.
     * BAN 상태는 어드민 액션이 아닌 일반 사용자 요청으로 해제 불가.
     */
    @Test
    @DisplayName("BANNED 멤버가 퇴장하면 예외가 발생한다")
    void leave_BANNED멤버_예외() {
        given(chatRoomMemberRepo.findByRoomAndMemberWithStatus(any(), any(), anyList()))
                .willReturn(Optional.of(member(MemberStatus.BANNED, MEMBER_ID)));

        assertThatThrownBy(() -> chatRoomMemberService.leave(ROOM_ID, MEMBER_ID))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * 마지막 멤버가 퇴장하면 방 상태가 EMPTY로 변경되어야 한다.
     * EMPTY 전이 누락 시 방이 사실상 비어 있어도 ACTIVE 상태로 남아 불필요한 리소스 점유.
     * 실패 포인트: countActiveByRoom=0 시 chatRoomRepo.save 호출 여부.
     * Mock: countActiveByRoom → 0 (마지막 멤버 퇴장 시뮬레이션)
     */
    @Test
    @DisplayName("마지막 멤버가 퇴장하면 방이 EMPTY 상태로 변경된다")
    void leave_마지막멤버퇴장_방EMPTY() {
        given(chatRoomMemberRepo.findByRoomAndMemberWithStatus(any(), any(), anyList()))
                .willReturn(Optional.of(member(MemberStatus.JOINED, MEMBER_ID)));
        given(chatRoomMemberRepo.saveAndFlush(any())).willAnswer(inv -> inv.getArgument(0));
        given(chatRoomMemberRepo.countActiveByRoom(any())).willReturn(0L);

        chatRoomMemberService.leave(ROOM_ID, MEMBER_ID);

        ArgumentCaptor<ChatRoom> captor = ArgumentCaptor.forClass(ChatRoom.class);
        verify(chatRoomRepo).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ChatRoomStatus.EMPTY);
    }

    // ──────────────────────── invite ────────────────────────

    @Test
    @DisplayName("신규 멤버를 채팅방에 초대한다")
    void invite_신규멤버_초대() {
        given(chatRoomRepo.findById(ChatRoomId.of(ROOM_ID))).willReturn(Optional.of(gameRoom()));
        given(chatRoomMemberRepo.existsJoinedByRoomAndMember(any(), eq(MemberId.of(MEMBER_ID)))).willReturn(true);
        given(chatRoomMemberRepo.findByRoomAndMemberWithStatus(any(), eq(MemberId.of(INVITEE_ID)), anyList()))
                .willReturn(Optional.empty());
        given(chatRoomMemberRepo.saveAndFlush(any())).willAnswer(inv -> inv.getArgument(0));

        ChatRoomMemberResponse result = chatRoomMemberService.invite(ROOM_ID, MEMBER_ID, INVITEE_ID);

        assertThat(result.memberId()).isEqualTo(INVITEE_ID);
        assertThat(result.status()).isEqualTo("INVITED");
    }

    @Test
    @DisplayName("LEFT 상태 멤버를 채팅방에 재초대한다")
    void invite_LEFT멤버_재초대() {
        given(chatRoomRepo.findById(ChatRoomId.of(ROOM_ID))).willReturn(Optional.of(gameRoom()));
        given(chatRoomMemberRepo.existsJoinedByRoomAndMember(any(), eq(MemberId.of(MEMBER_ID)))).willReturn(true);
        given(chatRoomMemberRepo.findByRoomAndMemberWithStatus(any(), eq(MemberId.of(INVITEE_ID)), anyList()))
                .willReturn(Optional.of(member(MemberStatus.LEFT, INVITEE_ID)));
        given(chatRoomMemberRepo.save(any())).willAnswer(inv -> inv.getArgument(0));

        ChatRoomMemberResponse result = chatRoomMemberService.invite(ROOM_ID, MEMBER_ID, INVITEE_ID);

        assertThat(result.memberId()).isEqualTo(INVITEE_ID);
        assertThat(result.status()).isEqualTo("INVITED");
    }

    /**
     * DIRECT 방에는 초대 기능이 없다. DIRECT 타입 방을 invite 시 즉시 거부.
     * 비즈니스 규칙: DM은 1:1로 고정, 추가 초대 시 방 특성이 훼손됨.
     * Mock: findById → chatRoom (BeforeEach의 DIRECT 방 재사용)
     */
    @Test
    @DisplayName("DIRECT 채팅방에는 멤버를 초대할 수 없다")
    void invite_DIRECT방_초대불가_예외() {
        assertThatThrownBy(() -> chatRoomMemberService.invite(ROOM_ID, MEMBER_ID, INVITEE_ID))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * 자기 자신을 초대하는 것은 의미가 없으며 데이터 오염을 유발한다.
     * DB 조회 전 requesterId == inviteeId 검증으로 빠르게 차단.
     */
    @Test
    @DisplayName("자기 자신을 초대하면 예외가 발생한다")
    void invite_자기자신초대_예외() {
        assertThatThrownBy(() -> chatRoomMemberService.invite(ROOM_ID, MEMBER_ID, MEMBER_ID))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * 이미 JOINED인 멤버를 다시 초대하면 중복 멤버십이 발생할 수 있다.
     * CONFLICT 예외로 호출자에게 현재 상태를 명확히 알려야 한다.
     * Mock: findByRoomAndMemberWithStatus → JOINED invitee
     */
    @Test
    @DisplayName("이미 JOINED 상태인 멤버를 초대하면 예외가 발생한다")
    void invite_이미JOINED멤버_초대_예외() {
        given(chatRoomRepo.findById(ChatRoomId.of(ROOM_ID))).willReturn(Optional.of(gameRoom()));
        given(chatRoomMemberRepo.existsJoinedByRoomAndMember(any(), eq(MemberId.of(MEMBER_ID)))).willReturn(true);
        given(chatRoomMemberRepo.findByRoomAndMemberWithStatus(any(), eq(MemberId.of(INVITEE_ID)), anyList()))
                .willReturn(Optional.of(member(MemberStatus.JOINED, INVITEE_ID)));

        assertThatThrownBy(() -> chatRoomMemberService.invite(ROOM_ID, MEMBER_ID, INVITEE_ID))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * BANNED 멤버는 재초대 경로로도 방에 다시 들어올 수 없다.
     * BAN은 모든 입장 경로를 차단하는 제재이며 초대로 우회되면 안 된다.
     * Mock: findByRoomAndMemberWithStatus → BANNED invitee
     */
    @Test
    @DisplayName("BANNED 멤버를 초대하면 예외가 발생한다")
    void invite_BANNED멤버_초대_예외() {
        given(chatRoomRepo.findById(ChatRoomId.of(ROOM_ID))).willReturn(Optional.of(gameRoom()));
        given(chatRoomMemberRepo.existsJoinedByRoomAndMember(any(), eq(MemberId.of(MEMBER_ID)))).willReturn(true);
        given(chatRoomMemberRepo.findByRoomAndMemberWithStatus(any(), eq(MemberId.of(INVITEE_ID)), anyList()))
                .willReturn(Optional.of(member(MemberStatus.BANNED, INVITEE_ID)));

        assertThatThrownBy(() -> chatRoomMemberService.invite(ROOM_ID, MEMBER_ID, INVITEE_ID))
                .isInstanceOf(BusinessException.class);
    }

    // ──────────────────────── ban ────────────────────────

    @Test
    @DisplayName("방장이 JOINED 멤버를 BAN 처리한다")
    void ban_멤버_BAN처리() {
        Long targetId = 3L;
        given(chatRoomRepo.findById(ChatRoomId.of(ROOM_ID))).willReturn(Optional.of(gameRoom()));
        given(chatRoomMemberRepo.findByRoomAndMemberWithStatus(any(), eq(MemberId.of(targetId)), anyList()))
                .willReturn(Optional.of(member(MemberStatus.JOINED, targetId)));
        given(chatRoomMemberRepo.save(any())).willAnswer(inv -> inv.getArgument(0));

        ChatRoomMemberResponse result = chatRoomMemberService.ban(ROOM_ID, MEMBER_ID, targetId);

        assertThat(result.memberId()).isEqualTo(targetId);
        assertThat(result.status()).isEqualTo("BANNED");
    }

    /**
     * BAN은 방장만 할 수 있다. 일반 멤버가 BAN 시도하면 FORBIDDEN 예외.
     * Mock: findById → gameRoom (방장=MEMBER_ID), 요청자=INVITEE_ID
     */
    @Test
    @DisplayName("방장이 아닌 멤버가 BAN을 요청하면 예외가 발생한다")
    void ban_비방장_예외() {
        given(chatRoomRepo.findById(ChatRoomId.of(ROOM_ID))).willReturn(Optional.of(gameRoom()));

        assertThatThrownBy(() -> chatRoomMemberService.ban(ROOM_ID, INVITEE_ID, 3L))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * 자기 자신을 BAN할 수 없다. requesterId == targetId 시 INVALID_INPUT 예외.
     * DB 조회 전에 검증이 이루어져야 불필요한 쿼리를 방지한다.
     */
    @Test
    @DisplayName("자기 자신을 BAN하면 예외가 발생한다")
    void ban_자기자신BAN_예외() {
        assertThatThrownBy(() -> chatRoomMemberService.ban(ROOM_ID, MEMBER_ID, MEMBER_ID))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * 이미 BANNED 상태인 멤버를 다시 BAN하면 CONFLICT 예외를 발생시켜야 한다.
     * 멱등성 없는 이중 BAN은 이벤트 중복 발행으로 클라이언트 구독 취소가 중복 발생할 수 있음.
     * 실패 포인트: isBanned() 체크가 누락되면 ban 이벤트가 중복 발행됨.
     * Mock: findByRoomAndMemberWithStatus → BANNED target
     */
    @Test
    @DisplayName("이미 BANNED 상태인 멤버를 다시 BAN하면 예외가 발생한다")
    void ban_이미BANNED_예외() {
        given(chatRoomRepo.findById(ChatRoomId.of(ROOM_ID))).willReturn(Optional.of(gameRoom()));
        given(chatRoomMemberRepo.findByRoomAndMemberWithStatus(any(), eq(MemberId.of(INVITEE_ID)), anyList()))
                .willReturn(Optional.of(member(MemberStatus.BANNED, INVITEE_ID)));

        assertThatThrownBy(() -> chatRoomMemberService.ban(ROOM_ID, MEMBER_ID, INVITEE_ID))
                .isInstanceOf(BusinessException.class);
    }

    // ──────────────────────── changeNotification ────────────────────────

    @Test
    @DisplayName("채팅방 알림 설정을 변경한다")
    void changeNotification_알림_변경() {
        given(chatRoomMemberRepo.findByRoomAndMemberWithStatus(any(), any(), anyList()))
                .willReturn(Optional.of(member(MemberStatus.JOINED, MEMBER_ID)));
        given(chatRoomMemberRepo.save(any())).willAnswer(inv -> inv.getArgument(0));

        ChatRoomMemberResponse result = chatRoomMemberService.changeNotification(ROOM_ID, MEMBER_ID, false);

        assertThat(result.roomId()).isEqualTo(ROOM_ID);
        assertThat(result.status()).isEqualTo("NOTIFICATION");
    }

    // ──────────────────────── 픽스처 헬퍼 ────────────────────────

    private ChatRoom gameRoom() {
        return ChatRoom.restore(
                ChatRoomId.of(ROOM_ID), ChatRoomName.of("한화 VS LG"), ChatRoomType.GAME,
                null, GameId.of(5L), NOW, NOW, ChatRoomStatus.ACTIVE, MemberId.of(MEMBER_ID)
        );
    }

    private ChatRoomMember member(MemberStatus status, Long memberId) {
        return ChatRoomMember.restore(
                100L, ChatRoomId.of(ROOM_ID), MemberId.of(memberId),
                status, true, NOW, NOW, null
        );
    }
}
