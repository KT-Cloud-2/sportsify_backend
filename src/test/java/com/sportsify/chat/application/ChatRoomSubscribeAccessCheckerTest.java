package com.sportsify.chat.application;

import com.sportsify.chat.application.webSocket.ChatRoomSubscribeAccessChecker;
import com.sportsify.chat.domain.model.chatRoom.*;
import com.sportsify.chat.domain.repository.ChatRoomMemberRepository;
import com.sportsify.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatRoomSubscribeAccessCheckerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 27, 12, 0);

    @InjectMocks
    private ChatRoomSubscribeAccessChecker checker;

    @Mock
    private ChatRoomMemberRepository chatRoomMemberRepository;

    // ──────────────────────── GAME 방 ────────────────────────

    @Test
    @DisplayName("ACTIVE GAME 방은 memberId 없이도 구독을 허용한다")
    void canSubscribe_ACTIVE_GAME_방_비인증_허용() {
        ChatRoom room = gameRoom(ChatRoomStatus.ACTIVE);

        boolean result = checker.canSubscribe(room, Optional.empty());

        assertThat(result).isTrue();
        verify(chatRoomMemberRepository, never()).existsJoinedByRoomAndMember(any(), any());
    }

    @Test
    @DisplayName("EMPTY GAME 방은 memberId 없이도 구독을 허용한다")
    void canSubscribe_EMPTY_GAME_방_비인증_허용() {
        ChatRoom room = gameRoom(ChatRoomStatus.EMPTY);

        boolean result = checker.canSubscribe(room, Optional.empty());

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("ARCHIVED GAME 방은 구독을 거부한다")
    void canSubscribe_ARCHIVED_GAME_방_거부() {
        ChatRoom room = gameRoom(ChatRoomStatus.ARCHIVED);

        boolean result = checker.canSubscribe(room, Optional.empty());

        assertThat(result).isFalse();
        verify(chatRoomMemberRepository, never()).existsJoinedByRoomAndMember(any(), any());
    }

    @Test
    @DisplayName("DELETED GAME 방은 구독을 거부한다")
    void canSubscribe_DELETED_GAME_방_거부() {
        ChatRoom room = gameRoom(ChatRoomStatus.DELETED);

        boolean result = checker.canSubscribe(room, Optional.empty());

        assertThat(result).isFalse();
    }

    // ──────────────────────── DIRECT 방 ────────────────────────

    @Test
    @DisplayName("ACTIVE DIRECT 방에서 JOINED 멤버는 구독을 허용한다")
    void canSubscribe_ACTIVE_DIRECT_방_JOINED멤버_허용() {
        ChatRoom room = directRoom(ChatRoomStatus.ACTIVE);
        MemberId memberId = MemberId.of(1L);
        given(chatRoomMemberRepository.existsJoinedByRoomAndMember(room.getId(), memberId)).willReturn(true);

        boolean result = checker.canSubscribe(room, Optional.of(memberId));

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("ACTIVE DIRECT 방에서 비멤버(미가입)는 구독을 거부한다")
    void canSubscribe_ACTIVE_DIRECT_방_비멤버_거부() {
        ChatRoom room = directRoom(ChatRoomStatus.ACTIVE);
        MemberId memberId = MemberId.of(1L);
        given(chatRoomMemberRepository.existsJoinedByRoomAndMember(room.getId(), memberId)).willReturn(false);

        boolean result = checker.canSubscribe(room, Optional.of(memberId));

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("ACTIVE DIRECT 방에서 memberId가 없으면 구독을 거부한다")
    void canSubscribe_ACTIVE_DIRECT_방_memberId없음_거부() {
        ChatRoom room = directRoom(ChatRoomStatus.ACTIVE);

        boolean result = checker.canSubscribe(room, Optional.empty());

        assertThat(result).isFalse();
        verify(chatRoomMemberRepository, never()).existsJoinedByRoomAndMember(any(), any());
    }

    @Test
    @DisplayName("EMPTY DIRECT 방에서 JOINED 멤버는 구독을 허용한다")
    void canSubscribe_EMPTY_DIRECT_방_JOINED멤버_허용() {
        ChatRoom room = directRoom(ChatRoomStatus.EMPTY);
        MemberId memberId = MemberId.of(2L);
        given(chatRoomMemberRepository.existsJoinedByRoomAndMember(room.getId(), memberId)).willReturn(true);

        boolean result = checker.canSubscribe(room, Optional.of(memberId));

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("ARCHIVED DIRECT 방은 JOINED 멤버라도 구독을 거부한다")
    void canSubscribe_ARCHIVED_DIRECT_방_거부() {
        ChatRoom room = directRoom(ChatRoomStatus.ARCHIVED);

        boolean result = checker.canSubscribe(room, Optional.of(MemberId.of(1L)));

        assertThat(result).isFalse();
        verify(chatRoomMemberRepository, never()).existsJoinedByRoomAndMember(any(), any());
    }

    @Test
    @DisplayName("DELETED DIRECT 방은 JOINED 멤버라도 구독을 거부한다")
    void canSubscribe_DELETED_DIRECT_방_거부() {
        ChatRoom room = directRoom(ChatRoomStatus.DELETED);

        boolean result = checker.canSubscribe(room, Optional.of(MemberId.of(1L)));

        assertThat(result).isFalse();
        verify(chatRoomMemberRepository, never()).existsJoinedByRoomAndMember(any(), any());
    }

    // ──────────────────────── 픽스처 헬퍼 ────────────────────────

    private ChatRoom gameRoom(ChatRoomStatus status) {
        return ChatRoom.restore(
                ChatRoomId.of(10L), ChatRoomName.of("게임방"), ChatRoomType.GAME, null,
                GameId.of(1L), NOW, NOW, status, MemberId.of(99L)
        );
    }

    private ChatRoom directRoom(ChatRoomStatus status) {
        return ChatRoom.restore(
                ChatRoomId.of(20L), ChatRoomName.of("DM"), ChatRoomType.DIRECT, null,
                null, NOW, NOW, status, MemberId.of(99L)
        );
    }

    private static <T> T any() {
        return org.mockito.ArgumentMatchers.any();
    }
}
