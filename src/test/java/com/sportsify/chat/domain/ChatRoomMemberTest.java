package com.sportsify.chat.domain;

import com.sportsify.chat.domain.model.chatRoom.ChatRoomId;
import com.sportsify.chat.domain.model.chatRoom.MemberId;
import com.sportsify.chat.domain.model.chatRoomMember.ChatRoomMember;
import com.sportsify.chat.domain.model.chatRoomMember.MemberStatus;
import com.sportsify.chat.domain.model.message.MessageId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ChatRoomMemberTest {

    private static final ChatRoomId ROOM_ID = ChatRoomId.of(1L);
    private static final MemberId INVITED_MEMBER_ID = MemberId.of(1L);
    private static final MemberId INVITER_MEMBER_ID = MemberId.of(2L);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 4, 12, 0);
    private static final LocalDateTime LATER = NOW.plusHours(1);

    // ──────────────────────── newJoin / newInvited ────────────────────────

    @Test
    @DisplayName("newJoin은 JOINED 상태로 멤버를 생성한다")
    void newJoin_초기상태_검증() {
        ChatRoomMember member = ChatRoomMember.newJoin(ROOM_ID, INVITED_MEMBER_ID, NOW);

        assertThat(member.getStatus()).isEqualTo(MemberStatus.JOINED);
        assertThat(member.isNotificationEnabled()).isTrue();
        assertThat(member.getLastReadMessageId()).isNull();
        assertThat(member.getJoinedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("newInvited는 INVITED 상태로 멤버를 생성한다")
    void newInvited_초기상태_검증() {
        ChatRoomMember member = ChatRoomMember.newInvited(ROOM_ID, INVITER_MEMBER_ID, INVITED_MEMBER_ID, NOW);

        assertThat(member.getStatus()).isEqualTo(MemberStatus.INVITED);
        assertThat(member.isNotificationEnabled()).isTrue();
        assertThat(member.getJoinedAt()).isEqualTo(NOW);
    }

    // ──────────────────────── accept ────────────────────────

    @Test
    @DisplayName("INVITED 멤버가 수락하면 JOINED 상태로 변경된다")
    void accept_INVITED_to_JOINED() {
        ChatRoomMember member = ChatRoomMember.newInvited(ROOM_ID, INVITER_MEMBER_ID, INVITED_MEMBER_ID, NOW);

        member.accept(LATER);

        assertThat(member.getStatus()).isEqualTo(MemberStatus.JOINED);
        assertThat(member.getUpdatedAt()).isEqualTo(LATER);
    }

    @Test
    @DisplayName("LEFT 멤버가 수락하면 JOINED 상태로 변경된다")
    void accept_LEFT_to_JOINED() {
        ChatRoomMember member = restored(MemberStatus.LEFT);

        member.accept(LATER);

        assertThat(member.getStatus()).isEqualTo(MemberStatus.JOINED);
        assertThat(member.getUpdatedAt()).isEqualTo(LATER);
    }

    @Test
    @DisplayName("이미 JOINED 멤버가 수락해도 updatedAt이 갱신되지 않는다")
    void accept_JOINED_멱등() {
        ChatRoomMember member = ChatRoomMember.newJoin(ROOM_ID, INVITED_MEMBER_ID, NOW);

        member.accept(LATER);

        assertThat(member.getStatus()).isEqualTo(MemberStatus.JOINED);
        assertThat(member.getUpdatedAt()).isEqualTo(NOW);
    }

    // ──────────────────────── leave ────────────────────────

    @Test
    @DisplayName("JOINED 멤버가 퇴장하면 LEFT 상태로 변경된다")
    void leave_JOINED_to_LEFT() {
        ChatRoomMember member = ChatRoomMember.newJoin(ROOM_ID, INVITED_MEMBER_ID, NOW);

        member.leave(LATER);

        assertThat(member.getStatus()).isEqualTo(MemberStatus.LEFT);
        assertThat(member.getUpdatedAt()).isEqualTo(LATER);
    }

    @Test
    @DisplayName("이미 LEFT 멤버가 퇴장해도 updatedAt이 갱신되지 않는다")
    void leave_LEFT_멱등() {
        ChatRoomMember member = restored(MemberStatus.LEFT);

        member.leave(LATER);

        assertThat(member.getStatus()).isEqualTo(MemberStatus.LEFT);
        assertThat(member.getUpdatedAt()).isEqualTo(NOW);
    }

    // ──────────────────────── delete ────────────────────────

    @Test
    @DisplayName("멤버를 삭제하면 DELETED 상태로 변경된다")
    void delete_상태변경() {
        ChatRoomMember member = ChatRoomMember.newJoin(ROOM_ID, INVITED_MEMBER_ID, NOW);

        member.delete(LATER);

        assertThat(member.getStatus()).isEqualTo(MemberStatus.DELETED);
        assertThat(member.getUpdatedAt()).isEqualTo(LATER);
    }

    @Test
    @DisplayName("이미 DELETED 멤버를 삭제해도 updatedAt이 갱신되지 않는다")
    void delete_DELETED_멱등() {
        ChatRoomMember member = restored(MemberStatus.DELETED);

        member.delete(LATER);

        assertThat(member.getStatus()).isEqualTo(MemberStatus.DELETED);
        assertThat(member.getUpdatedAt()).isEqualTo(NOW);
    }

    // ──────────────────────── ban ────────────────────────

    @Test
    @DisplayName("멤버를 BAN하면 BANNED 상태로 변경된다")
    void ban_상태변경() {
        ChatRoomMember member = ChatRoomMember.newJoin(ROOM_ID, INVITED_MEMBER_ID, NOW);

        member.ban(LATER);

        assertThat(member.getStatus()).isEqualTo(MemberStatus.BANNED);
        assertThat(member.isBanned()).isTrue();
        assertThat(member.getUpdatedAt()).isEqualTo(LATER);
    }

    @Test
    @DisplayName("이미 BANNED 멤버를 BAN해도 updatedAt이 갱신되지 않는다")
    void ban_BANNED_멱등() {
        ChatRoomMember member = restored(MemberStatus.BANNED);

        member.ban(LATER);

        assertThat(member.getUpdatedAt()).isEqualTo(NOW);
    }

    // ──────────────────────── changeNotification ────────────────────────

    @Test
    @DisplayName("알림을 끄면 notificationEnabled가 false로 변경된다")
    void changeNotification_off() {
        ChatRoomMember member = ChatRoomMember.newJoin(ROOM_ID, INVITED_MEMBER_ID, NOW);

        member.changeNotification(false, LATER);

        assertThat(member.isNotificationEnabled()).isFalse();
        assertThat(member.getUpdatedAt()).isEqualTo(LATER);
    }

    @Test
    @DisplayName("동일한 값으로 변경하면 updatedAt이 갱신되지 않는다")
    void changeNotification_동일값_무시() {
        ChatRoomMember member = ChatRoomMember.newJoin(ROOM_ID, INVITED_MEMBER_ID, NOW);

        member.changeNotification(true, LATER);

        assertThat(member.getUpdatedAt()).isEqualTo(NOW);
    }

    // ──────────────────────── updateLastReadMessage ────────────────────────

    @Test
    @DisplayName("JOINED 멤버가 마지막 읽은 메시지를 갱신한다")
    void updateLastReadMessage_최초갱신() {
        ChatRoomMember member = ChatRoomMember.newJoin(ROOM_ID, INVITED_MEMBER_ID, NOW);

        member.updateLastReadMessage(MessageId.of(100L), LATER);

        assertThat(member.getLastReadMessageId()).isEqualTo(100L);
        assertThat(member.getUpdatedAt()).isEqualTo(LATER);
    }

    @Test
    @DisplayName("더 큰 messageId로 재갱신하면 lastReadMessageId가 교체된다")
    void updateLastReadMessage_큰id_갱신() {
        ChatRoomMember member = ChatRoomMember.restore(1L, ROOM_ID, INVITED_MEMBER_ID, MemberStatus.JOINED, true, NOW, NOW, 100L);

        member.updateLastReadMessage(MessageId.of(200L), LATER);

        assertThat(member.getLastReadMessageId()).isEqualTo(200L);
    }

    @Test
    @DisplayName("더 작은 messageId는 lastReadMessageId를 갱신하지 않는다")
    void updateLastReadMessage_작은id_무시() {
        ChatRoomMember member = ChatRoomMember.restore(1L, ROOM_ID, INVITED_MEMBER_ID, MemberStatus.JOINED, true, NOW, NOW, 100L);

        member.updateLastReadMessage(MessageId.of(50L), LATER);

        assertThat(member.getLastReadMessageId()).isEqualTo(100L);
        assertThat(member.getUpdatedAt()).isEqualTo(NOW);
    }

    // ──────────────────────── changeStatusToInvite ────────────────────────

    @Test
    @DisplayName("LEFT 멤버를 재초대하면 INVITED 상태로 변경된다")
    void changeStatusToInvite_LEFT_to_INVITED() {
        ChatRoomMember member = restored(MemberStatus.LEFT);

        member.changeStatusToInvite(LATER);

        assertThat(member.getStatus()).isEqualTo(MemberStatus.INVITED);
        assertThat(member.getUpdatedAt()).isEqualTo(LATER);
    }

    @Test
    @DisplayName("이미 INVITED 멤버를 재초대해도 updatedAt이 갱신되지 않는다")
    void changeStatusToInvite_INVITED_멱등() {
        ChatRoomMember member = restored(MemberStatus.INVITED);

        member.changeStatusToInvite(LATER);

        assertThat(member.getStatus()).isEqualTo(MemberStatus.INVITED);
        assertThat(member.getUpdatedAt()).isEqualTo(NOW);
    }

    // ──────────────────────── rejectInvite ────────────────────────

    @Test
    @DisplayName("INVITED 멤버가 초대를 거부하면 REJECT 상태로 변경되고 MEMBER_REJECTED 이벤트가 등록된다")
    void rejectInvite_INVITED_to_REJECT() {
        ChatRoomMember member = restored(MemberStatus.INVITED);

        member.rejectInvite(LATER);

        assertThat(member.getStatus()).isEqualTo(MemberStatus.REJECT);
        assertThat(member.getUpdatedAt()).isEqualTo(LATER);
        assertThat(member.getEvents()).hasSize(1);
    }

    @Test
    @DisplayName("이미 REJECT 상태인 멤버가 거부해도 updatedAt이 갱신되지 않는다")
    void rejectInvite_REJECT_멱등() {
        ChatRoomMember member = restored(MemberStatus.REJECT);

        member.rejectInvite(LATER);

        assertThat(member.getStatus()).isEqualTo(MemberStatus.REJECT);
        assertThat(member.getUpdatedAt()).isEqualTo(NOW);
    }

    // ──────────────────────── assignId ────────────────────────

    @Test
    @DisplayName("새 멤버에 id를 부여한다")
    void assignId_부여성공() {
        ChatRoomMember member = ChatRoomMember.newJoin(ROOM_ID, INVITED_MEMBER_ID, NOW);

        member.assignId(99L);

        assertThat(member.getId()).isEqualTo(99L);
    }

    // ──────────────────────── 상태 체크 메서드 ────────────────────────

    @Test
    @DisplayName("JOINED 멤버는 isJoined가 true, isInvited/isBanned는 false다")
    void 상태체크_JOINED() {
        ChatRoomMember member = ChatRoomMember.newJoin(ROOM_ID, INVITED_MEMBER_ID, NOW);

        assertThat(member.isJoined()).isTrue();
        assertThat(member.isInvited()).isFalse();
        assertThat(member.isBanned()).isFalse();
    }

    @Test
    @DisplayName("belongsTo는 같은 roomId이면 true, 다른 roomId이면 false를 반환한다")
    void belongsTo_검증() {
        ChatRoomMember member = ChatRoomMember.newJoin(ROOM_ID, INVITED_MEMBER_ID, NOW);

        assertThat(member.belongsTo(ROOM_ID)).isTrue();
        assertThat(member.belongsTo(ChatRoomId.of(999L))).isFalse();
    }

    @Test
    @DisplayName("is는 같은 memberId이면 true, 다른 memberId이면 false를 반환한다")
    void is_검증() {
        ChatRoomMember member = ChatRoomMember.newJoin(ROOM_ID, INVITED_MEMBER_ID, NOW);

        assertThat(member.is(INVITED_MEMBER_ID)).isTrue();
        assertThat(member.is(MemberId.of(999L))).isFalse();
    }

    // ──────────────────────── 픽스처 헬퍼 ────────────────────────

    private ChatRoomMember restored(MemberStatus status) {
        return ChatRoomMember.restore(1L, ROOM_ID, INVITED_MEMBER_ID, status, true, NOW, NOW, null);
    }
}
