package com.sportsify.chat.application;

import com.sportsify.chat.application.message.dto.*;
import com.sportsify.chat.application.message.service.MessageService;
import com.sportsify.chat.domain.model.chatRoom.*;
import com.sportsify.chat.domain.model.chatRoomMember.ChatRoomMember;
import com.sportsify.chat.domain.model.chatRoomMember.MemberStatus;
import com.sportsify.chat.domain.model.message.*;
import com.sportsify.chat.domain.repository.ChatRoomMemberRepository;
import com.sportsify.chat.domain.repository.ChatRoomRepository;
import com.sportsify.chat.domain.repository.MessageRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    private static final Instant NOW_INSTANT = Instant.parse("2026-05-04T12:00:00Z");
    private static final LocalDateTime NOW = LocalDateTime.ofInstant(NOW_INSTANT, ZoneOffset.UTC);

    @InjectMocks
    private MessageService messageService;

    @Mock
    private MessageRepository messageRepo;

    @Mock
    private ChatRoomRepository chatRoomRepo;

    @Mock
    private ChatRoomMemberRepository chatRoomMemberRepo;

    @Mock
    private Clock clock;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Mock
    private StringRedisTemplate redisTemplate;

    // ──────────────────────── send ────────────────────────

    @Test
    @DisplayName("ACTIVE 채팅방에서 JOINED 멤버가 메시지를 전송한다")
    void send_메시지전송성공() {
        stubClock();
        ChatRoom room = chatRoom(10L, ChatRoomType.GAME);
        ChatRoomMember member = joinedMember(10L, 1L);
        Message saved = message(100L, 10L, 1L, "안녕하세요", MessageStatus.ACTIVE);

        given(chatRoomRepo.findByIdForUpdateWrite(ChatRoomId.of(10L))).willReturn(Optional.of(room));
        given(chatRoomMemberRepo.findByRoomAndMemberForUpdate(ChatRoomId.of(10L), MemberId.of(1L))).willReturn(Optional.of(member));
        given(messageRepo.save(any())).willReturn(saved);

        MessageCreateResponse result = messageService.send(
                new MessageCreateRequest(null, 10L, "TEXT", "안녕하세요"), 1L);

        assertThat(result.messageId()).isEqualTo(100L);
        assertThat(result.createdAt()).isEqualTo(NOW_INSTANT);
    }

    // ──────────────────────── delete ────────────────────────

    @Test
    @DisplayName("메시지 작성자가 자신의 메시지를 삭제한다")
    void delete_메시지삭제성공() {
        stubClock();
        Message msg = message(100L, 10L, 1L, "삭제할 메시지", MessageStatus.ACTIVE);

        given(messageRepo.findByIdForUpdate(MessageId.of(100L))).willReturn(Optional.of(msg));
        given(messageRepo.save(any())).willAnswer(inv -> inv.getArgument(0));

        MessageDeleteResponse result = messageService.delete(100L, 1L);

        assertThat(result.messageId()).isEqualTo(100L);
        assertThat(result.roomId()).isEqualTo(10L);
        assertThat(result.status()).isEqualTo("DELETED");
    }

    // ──────────────────────── getHistory ────────────────────────

    @Test
    @DisplayName("채팅 이력 조회 시 다음 페이지가 없으면 nextCursor가 null이다")
    void getHistory_마지막페이지() {
        List<Message> messages = List.of(
                message(10L, 1L, 1L, "msg1", MessageStatus.ACTIVE),
                message(11L, 1L, 1L, "msg2", MessageStatus.ACTIVE)
        );
        given(messageRepo.findByRoomAndMemberBefore(
                eq(ChatRoomId.of(1L)), eq(MemberId.of(1L)), isNull(), eq(21)))
                .willReturn(messages);

        MessageListResponse result = messageService.getHistory(
                new MessagePageNationRequest(null, 20), 1L, 1L);

        assertThat(result.totalCount()).isEqualTo(2);
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
        assertThat(result.messages()).hasSize(2);
    }

    @Test
    @DisplayName("채팅 이력 조회 시 다음 페이지가 있으면 nextCursor가 반환된다")
    void getHistory_다음페이지있음() {
        List<Message> messages = List.of(
                message(10L, 1L, 1L, "msg1", MessageStatus.ACTIVE),
                message(11L, 1L, 1L, "msg2", MessageStatus.ACTIVE),
                message(12L, 1L, 1L, "msg3", MessageStatus.ACTIVE)
        );
        given(messageRepo.findByRoomAndMemberBefore(
                eq(ChatRoomId.of(1L)), eq(MemberId.of(1L)), isNull(), eq(3)))
                .willReturn(messages);

        MessageListResponse result = messageService.getHistory(
                new MessagePageNationRequest(null, 2), 1L, 1L);

        assertThat(result.hasNext()).isTrue();
        assertThat(result.nextCursor()).isEqualTo(11L);
        assertThat(result.totalCount()).isEqualTo(2);
        assertThat(result.messages()).hasSize(2);
    }

    // ──────────────────────── getMessages ────────────────────────

    @Test
    @DisplayName("GAME 채팅방 메시지를 조회한다")
    void getMessages_GAME채팅방_메시지조회() {
        ChatRoom room = chatRoom(10L, ChatRoomType.GAME);
        List<Message> messages = List.of(
                message(50L, 10L, 2L, "hello", MessageStatus.ACTIVE),
                message(51L, 10L, 2L, "world", MessageStatus.ACTIVE)
        );

        given(chatRoomRepo.findByIdForUpdateRead(ChatRoomId.of(10L))).willReturn(Optional.of(room));
        given(chatRoomMemberRepo.existsJoinedByRoomAndMember(ChatRoomId.of(10L), MemberId.of(1L))).willReturn(true);
        given(messageRepo.findByRoomBefore(eq(ChatRoomId.of(10L)), isNull(), eq(21))).willReturn(messages);

        MessageListResponse result = messageService.getMessages(
                new MessagePageNationRequest(null, 20), 10L, 1L);

        assertThat(result.totalCount()).isEqualTo(2);
        assertThat(result.hasNext()).isFalse();
        assertThat(result.messages()).hasSize(2);
    }

    @Test
    @DisplayName("DIRECT 채팅방에서 멤버가 메시지를 조회한다")
    void getMessages_DIRECT채팅방_멤버조회() {
        ChatRoom room = ChatRoom.restore(
                ChatRoomId.of(20L), ChatRoomName.of("DM"), ChatRoomType.DIRECT, null,
                null, NOW, NOW, ChatRoomStatus.ACTIVE, MemberId.of(1L));
        List<Message> messages = List.of(
                message(60L, 20L, 1L, "DM메시지", MessageStatus.ACTIVE)
        );

        given(chatRoomRepo.findByIdForUpdateRead(ChatRoomId.of(20L))).willReturn(Optional.of(room));
        given(chatRoomMemberRepo.existsJoinedByRoomAndMember(ChatRoomId.of(20L), MemberId.of(1L))).willReturn(true);
        given(messageRepo.findByRoomBefore(eq(ChatRoomId.of(20L)), isNull(), eq(21))).willReturn(messages);
        given(chatRoomMemberRepo.findLastMessageIdsAndMemberIdsByRoomId(ChatRoomId.of(20L))).willReturn(Map.of());

        MessageListResponse result = messageService.getMessages(
                new MessagePageNationRequest(null, 20), 20L, 1L);

        assertThat(result.totalCount()).isEqualTo(1);
        assertThat(result.messages()).hasSize(1);
    }

    @Test
    @DisplayName("메시지 조회 시 다음 페이지가 있으면 nextCursor가 반환된다")
    void getMessages_다음페이지있음() {
        ChatRoom room = chatRoom(10L, ChatRoomType.GAME);
        List<Message> messages = List.of(
                message(50L, 10L, 2L, "msg1", MessageStatus.ACTIVE),
                message(51L, 10L, 2L, "msg2", MessageStatus.ACTIVE),
                message(52L, 10L, 2L, "msg3", MessageStatus.ACTIVE)
        );

        given(chatRoomRepo.findByIdForUpdateRead(ChatRoomId.of(10L))).willReturn(Optional.of(room));
        given(chatRoomMemberRepo.existsJoinedByRoomAndMember(ChatRoomId.of(10L), MemberId.of(1L))).willReturn(true);
        given(messageRepo.findByRoomBefore(eq(ChatRoomId.of(10L)), isNull(), eq(3))).willReturn(messages);

        MessageListResponse result = messageService.getMessages(
                new MessagePageNationRequest(null, 2), 10L, 1L);

        assertThat(result.hasNext()).isTrue();
        assertThat(result.nextCursor()).isEqualTo(51L);
        assertThat(result.totalCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("익명 사용자가 GAME 채팅방 메시지를 조회한다")
    void getMessages_익명사용자_GAME방_조회성공() {
        ChatRoom room = chatRoom(10L, ChatRoomType.GAME);
        List<Message> messages = List.of(
                message(50L, 10L, 2L, "공개메시지", MessageStatus.ACTIVE)
        );

        given(chatRoomRepo.findByIdForUpdateRead(ChatRoomId.of(10L))).willReturn(Optional.of(room));
        given(messageRepo.findByRoomBefore(eq(ChatRoomId.of(10L)), isNull(), eq(21))).willReturn(messages);

        MessageListResponse result = messageService.getMessages(
                new MessagePageNationRequest(null, 20), 10L, null);

        assertThat(result.totalCount()).isEqualTo(1);
        assertThat(result.messages()).hasSize(1);
    }

    // ──────────────────────── read ────────────────────────

    @Test
    @DisplayName("needDirectCheck=true이고 DIRECT 방이면 Redis에 lastRead를 기록한다")
    void read_DIRECT방_Redis기록() {
        ChatRoom directRoom = ChatRoom.restore(
                ChatRoomId.of(10L), ChatRoomName.of("DM"), ChatRoomType.DIRECT, null,
                null, NOW, NOW, ChatRoomStatus.ACTIVE, MemberId.of(1L));
        given(chatRoomRepo.findById(ChatRoomId.of(10L))).willReturn(Optional.of(directRoom));

        messageService.read(10L, 1L, 100L, true);

        org.mockito.Mockito.verify(redisTemplate).execute(any(), Collections.singletonList(any()), any(), any());
    }

    @Test
    @DisplayName("needDirectCheck=true이고 GAME 방이면 Redis 기록을 건너뛴다")
    void read_GAME방_Redis스킵() {
        ChatRoom gameRoom = chatRoom(10L, ChatRoomType.GAME);
        given(chatRoomRepo.findById(ChatRoomId.of(10L))).willReturn(Optional.of(gameRoom));

        messageService.read(10L, 1L, 100L, true);

        org.mockito.Mockito.verifyNoInteractions(redisTemplate);
    }

    // ──────────────────────── 픽스처 헬퍼 ────────────────────────

    private void stubClock() {
        given(clock.instant()).willReturn(NOW_INSTANT);
    }

    private ChatRoom chatRoom(Long id, ChatRoomType type) {
        return ChatRoom.restore(
                ChatRoomId.of(id), ChatRoomName.of("테스트 채팅방"), type, null,
                type == ChatRoomType.GAME ? GameId.of(5L) : null,
                NOW, NOW, ChatRoomStatus.ACTIVE, MemberId.of(1L)
        );
    }

    private ChatRoomMember joinedMember(Long roomId, Long memberId) {
        return ChatRoomMember.restore(
                1L, ChatRoomId.of(roomId), MemberId.of(memberId),
                MemberStatus.JOINED, true, NOW, NOW, null
        );
    }

    private Message message(Long id, Long roomId, Long senderId, String content, MessageStatus status) {
        return Message.restore(
                MessageId.of(id), ChatRoomId.of(roomId), MemberId.of(senderId),
                MessageContent.of(content), MessageType.TEXT, status, NOW_INSTANT
        );
    }
}
