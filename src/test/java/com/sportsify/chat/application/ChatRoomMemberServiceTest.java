package com.sportsify.chat.application;

import com.sportsify.chat.application.chatRoomMember.dto.ChatRoomMemberResponse;
import com.sportsify.chat.application.chatRoomMember.service.ChatRoomMemberService;
import com.sportsify.chat.domain.model.chatRoom.*;
import com.sportsify.chat.domain.model.chatRoomMember.ChatRoomMember;
import com.sportsify.chat.domain.model.chatRoomMember.MemberStatus;
import com.sportsify.chat.domain.repository.ChatRoomMemberRepository;
import com.sportsify.chat.domain.repository.ChatRoomRepository;
import com.sportsify.chat.infrastructure.persistence.lock.AdvisoryLockAdaptor;
import com.sportsify.member.domain.model.Member;
import com.sportsify.member.domain.model.OAuthProvider;
import com.sportsify.member.domain.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

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

    // [!경고] GAME이 구현이 완료되지 않은 관계로 제대로된 테스트를 하지 못하고 있습니다. 이후 해당 SERVICE가 구현되면 INSERT 후 테스트 해야 합니다.
    @Test
    @DisplayName("신규 멤버가 GAME 채팅방에 입장한다")
    void join_신규멤버_GAME방_입장() {
        stubClock();
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
        stubClock();
        given(chatRoomRepo.findById(ChatRoomId.of(ROOM_ID))).willReturn(Optional.of(gameRoom()));
        given(advisoryLockAdaptor.tryAcquireXactLock(any())).willReturn(true);
        given(chatRoomMemberRepo.findByRoomAndMemberWithStatus(any(), any(), anyList()))
                .willReturn(Optional.of(member(MemberStatus.INVITED, MEMBER_ID)));
        given(chatRoomMemberRepo.save(any())).willAnswer(inv -> inv.getArgument(0));

        ChatRoomMemberResponse result = chatRoomMemberService.join(ROOM_ID, MEMBER_ID);

        assertThat(result.status()).isEqualTo("JOINED");
    }

    // ──────────────────────── leave ────────────────────────

    @Test
    @DisplayName("JOINED 멤버가 채팅방을 퇴장한다")
    void leave_퇴장() {
        stubClock();
        given(chatRoomMemberRepo.findByRoomAndMemberWithStatus(any(), any(), anyList()))
                .willReturn(Optional.of(member(MemberStatus.JOINED, MEMBER_ID)));
        given(chatRoomMemberRepo.save(any())).willAnswer(inv -> inv.getArgument(0));

        ChatRoomMemberResponse result = chatRoomMemberService.leave(ROOM_ID, MEMBER_ID);

        assertThat(result.status()).isEqualTo("LEFT");
    }

    // ──────────────────────── invite ────────────────────────

    @Test
    @DisplayName("신규 멤버를 채팅방에 초대한다")
    void invite_신규멤버_초대() {
        stubClock();
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
        stubClock();
        given(chatRoomRepo.findById(ChatRoomId.of(ROOM_ID))).willReturn(Optional.of(gameRoom()));
        given(chatRoomMemberRepo.existsJoinedByRoomAndMember(any(), eq(MemberId.of(MEMBER_ID)))).willReturn(true);
        given(chatRoomMemberRepo.findByRoomAndMemberWithStatus(any(), eq(MemberId.of(INVITEE_ID)), anyList()))
                .willReturn(Optional.of(member(MemberStatus.LEFT, INVITEE_ID)));
        given(chatRoomMemberRepo.save(any())).willAnswer(inv -> inv.getArgument(0));

        ChatRoomMemberResponse result = chatRoomMemberService.invite(ROOM_ID, MEMBER_ID, INVITEE_ID);

        assertThat(result.memberId()).isEqualTo(INVITEE_ID);
        assertThat(result.status()).isEqualTo("INVITED");
    }

    // ──────────────────────── ban ────────────────────────

    @Test
    @DisplayName("방장이 JOINED 멤버를 BAN 처리한다")
    void ban_멤버_BAN처리() {
        stubClock();
        Long targetId = 3L;
        given(chatRoomRepo.findById(ChatRoomId.of(ROOM_ID))).willReturn(Optional.of(gameRoom()));
        given(chatRoomMemberRepo.findByRoomAndMemberWithStatus(any(), eq(MemberId.of(targetId)), anyList()))
                .willReturn(Optional.of(member(MemberStatus.JOINED, targetId)));
        given(chatRoomMemberRepo.save(any())).willAnswer(inv -> inv.getArgument(0));

        ChatRoomMemberResponse result = chatRoomMemberService.ban(ROOM_ID, MEMBER_ID, targetId);

        assertThat(result.memberId()).isEqualTo(targetId);
        assertThat(result.status()).isEqualTo("BANNED");
    }

    // ──────────────────────── changeNotification ────────────────────────

    @Test
    @DisplayName("채팅방 알림 설정을 변경한다")
    void changeNotification_알림_변경() {
        stubClock();
        given(chatRoomMemberRepo.findByRoomAndMemberWithStatus(any(), any(), anyList()))
                .willReturn(Optional.of(member(MemberStatus.JOINED, MEMBER_ID)));
        given(chatRoomMemberRepo.save(any())).willAnswer(inv -> inv.getArgument(0));

        ChatRoomMemberResponse result = chatRoomMemberService.changeNotification(ROOM_ID, MEMBER_ID, false);

        assertThat(result.roomId()).isEqualTo(ROOM_ID);
        assertThat(result.status()).isEqualTo("JOINED");
    }

    // ──────────────────────── 픽스처 헬퍼 ────────────────────────

    private void stubClock() {
        given(clock.instant()).willReturn(FIXED_INSTANT);
        given(clock.getZone()).willReturn(ZoneOffset.UTC);
    }

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
