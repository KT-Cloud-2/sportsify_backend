package com.sportsify.chat.application;

import com.sportsify.chat.application.chatRoomMember.dto.ChatRoomMemberResponse;
import com.sportsify.chat.application.chatRoomMember.service.ChatRoomMemberService;
import com.sportsify.chat.domain.model.chatRoom.*;
import com.sportsify.chat.domain.model.chatRoomMember.ChatRoomMember;
import com.sportsify.chat.domain.model.chatRoomMember.MemberStatus;
import com.sportsify.chat.domain.repository.ChatRoomMemberRepository;
import com.sportsify.chat.domain.repository.ChatRoomRepository;
import com.sportsify.chat.infrastructure.persistence.lock.AdvisoryLockAdaptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ChatRoomMemberServiceTest {

    private static final Instant FIXED = Instant.parse("2026-05-04T12:00:00Z");
    private static final LocalDateTime NOW = LocalDateTime.ofInstant(FIXED, ZoneOffset.UTC);
    private static final Long ROOM_ID = 10L;
    private static final Long MEMBER_ID = 1L;
    private static final Long INVITEE_ID = 2L;
    @InjectMocks
    private ChatRoomMemberService chatRoomMemberService;
    @Mock
    private ChatRoomRepository chatRoomRepo;
    @Mock
    private ChatRoomMemberRepository chatRoomMemberRepo;
    @Mock
    private Clock clock;
    @Mock
    private AdvisoryLockAdaptor advisoryLockAdaptor;

    // ──────────────────────── join ────────────────────────

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
        given(clock.instant()).willReturn(FIXED);
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
