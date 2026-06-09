package com.sportsify.chat.application;

import com.sportsify.chat.application.message.dto.*;
import com.sportsify.chat.application.message.service.MessageService;
import com.sportsify.chat.domain.model.chatRoom.*;
import com.sportsify.chat.domain.model.chatRoomMember.ChatRoomMember;
import com.sportsify.chat.domain.model.chatRoomMember.MemberStatus;
import com.sportsify.chat.domain.model.message.*;
import com.sportsify.chat.domain.model.chatRoom.MemberId;
import com.sportsify.chat.domain.model.message.MessageId;
import com.sportsify.chat.domain.repository.ChatRoomMemberRepository;
import com.sportsify.chat.domain.repository.ChatRoomRepository;
import com.sportsify.chat.domain.repository.MessageRepository;
import com.sportsify.chat.domain.repository.ReadCache;
import com.sportsify.common.exception.BusinessException;
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
import static org.mockito.Mockito.verifyNoInteractions;

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
    private ReadCache readCache;

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

    /**
     * 존재하지 않는 방에 메시지 전송 시 NOT_FOUND 예외.
     * findByIdForUpdateWrite가 empty를 반환하는 경우로, DB 조회 실패 상황을 명확하게 처리하는지 검증.
     * Mock: findByIdForUpdateWrite → Optional.empty
     */
    @Test
    @DisplayName("존재하지 않는 채팅방에 메시지 전송 시 예외가 발생한다")
    void send_채팅방없음_예외() {
        stubClock();
        given(chatRoomRepo.findByIdForUpdateWrite(any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> messageService.send(
                new MessageCreateRequest(null, 10L, "TEXT", "안녕"), 1L))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * ARCHIVED 방에는 메시지를 전송할 수 없다.
     * 아카이브 중인 방에 메시지가 쌓이면 아카이브 의도가 훼손됨.
     * 실패 포인트: switch에서 ARCHIVED 케이스 누락 시 메시지가 저장됨.
     * Mock: findByIdForUpdateWrite → ARCHIVED 방 반환
     */
    @Test
    @DisplayName("ARCHIVED 채팅방에 메시지 전송 시 예외가 발생한다")
    void send_ARCHIVED방_예외() {
        stubClock();
        ChatRoom archived = ChatRoom.restore(
                ChatRoomId.of(10L), ChatRoomName.of("방"), ChatRoomType.GAME, null,
                GameId.of(5L), NOW, NOW, ChatRoomStatus.ARCHIVED, MemberId.of(1L));
        given(chatRoomRepo.findByIdForUpdateWrite(ChatRoomId.of(10L))).willReturn(Optional.of(archived));

        assertThatThrownBy(() -> messageService.send(
                new MessageCreateRequest(null, 10L, "TEXT", "안녕"), 1L))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(messageRepo);
    }

    /**
     * DELETED 방에는 메시지를 전송할 수 없다.
     * 삭제된 방의 메시지 저장은 데이터 무결성 위반이며 조회 API에서도 반환되지 않아 유실됨.
     * Mock: findByIdForUpdateWrite → DELETED 방 반환
     */
    @Test
    @DisplayName("DELETED 채팅방에 메시지 전송 시 예외가 발생한다")
    void send_DELETED방_예외() {
        stubClock();
        ChatRoom deleted = ChatRoom.restore(
                ChatRoomId.of(10L), ChatRoomName.of("방"), ChatRoomType.GAME, null,
                GameId.of(5L), NOW, NOW, ChatRoomStatus.DELETED, MemberId.of(1L));
        given(chatRoomRepo.findByIdForUpdateWrite(ChatRoomId.of(10L))).willReturn(Optional.of(deleted));

        assertThatThrownBy(() -> messageService.send(
                new MessageCreateRequest(null, 10L, "TEXT", "안녕"), 1L))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(messageRepo);
    }

    /**
     * 방의 멤버가 아닌 사용자는 메시지를 전송할 수 없다.
     * findByRoomAndMemberForUpdate가 empty 반환 시 FORBIDDEN 예외.
     * Mock: findByIdForUpdateWrite → ACTIVE 방, findByRoomAndMemberForUpdate → empty
     */
    @Test
    @DisplayName("채팅방 멤버가 아닌 사용자가 메시지 전송 시 예외가 발생한다")
    void send_멤버아님_예외() {
        stubClock();
        given(chatRoomRepo.findByIdForUpdateWrite(ChatRoomId.of(10L))).willReturn(Optional.of(chatRoom(10L, ChatRoomType.GAME)));
        given(chatRoomMemberRepo.findByRoomAndMemberForUpdate(any(), any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> messageService.send(
                new MessageCreateRequest(null, 10L, "TEXT", "안녕"), 1L))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * BANNED 멤버는 메시지 전송이 차단된다. BAN 제재의 핵심 효과.
     * 실패 포인트: switch에서 BANNED 케이스 누락 시 BAN 멤버가 메시지를 전송함.
     * Mock: findByRoomAndMemberForUpdate → BANNED 멤버 반환
     */
    @Test
    @DisplayName("BANNED 멤버가 메시지 전송 시 예외가 발생한다")
    void send_BANNED멤버_예외() {
        stubClock();
        given(chatRoomRepo.findByIdForUpdateWrite(ChatRoomId.of(10L))).willReturn(Optional.of(chatRoom(10L, ChatRoomType.GAME)));
        given(chatRoomMemberRepo.findByRoomAndMemberForUpdate(any(), any()))
                .willReturn(Optional.of(memberWithStatus(10L, 1L, MemberStatus.BANNED)));

        assertThatThrownBy(() -> messageService.send(
                new MessageCreateRequest(null, 10L, "TEXT", "안녕"), 1L))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(messageRepo);
    }

    /**
     * LEFT(퇴장) 멤버는 재입장 없이 메시지를 전송할 수 없다.
     * Mock: findByRoomAndMemberForUpdate → LEFT 멤버 반환
     */
    @Test
    @DisplayName("LEFT 멤버가 메시지 전송 시 예외가 발생한다")
    void send_LEFT멤버_예외() {
        stubClock();
        given(chatRoomRepo.findByIdForUpdateWrite(ChatRoomId.of(10L))).willReturn(Optional.of(chatRoom(10L, ChatRoomType.GAME)));
        given(chatRoomMemberRepo.findByRoomAndMemberForUpdate(any(), any()))
                .willReturn(Optional.of(memberWithStatus(10L, 1L, MemberStatus.LEFT)));

        assertThatThrownBy(() -> messageService.send(
                new MessageCreateRequest(null, 10L, "TEXT", "안녕"), 1L))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * INVITED(초대 수락 전) 멤버는 아직 방 구성원이 아니므로 메시지 전송 불가.
     * 초대 수락 전 메시지 전송을 허용하면 방 입장 전 메시지가 표시되는 UX 오류 발생.
     * Mock: findByRoomAndMemberForUpdate → INVITED 멤버 반환
     */
    @Test
    @DisplayName("초대 수락 전(INVITED) 멤버가 메시지 전송 시 예외가 발생한다")
    void send_INVITED멤버_예외() {
        stubClock();
        given(chatRoomRepo.findByIdForUpdateWrite(ChatRoomId.of(10L))).willReturn(Optional.of(chatRoom(10L, ChatRoomType.GAME)));
        given(chatRoomMemberRepo.findByRoomAndMemberForUpdate(any(), any()))
                .willReturn(Optional.of(memberWithStatus(10L, 1L, MemberStatus.INVITED)));

        assertThatThrownBy(() -> messageService.send(
                new MessageCreateRequest(null, 10L, "TEXT", "안녕"), 1L))
                .isInstanceOf(BusinessException.class);
    }

    // ──────────────────────── delete ────────────────────────

    @Test
    @DisplayName("메시지 작성자가 자신의 메시지를 삭제한다")
    void delete_메시지삭제성공() {
        stubClock();
        Message msg = message(100L, 10L, 1L, "삭제할 메시지", MessageStatus.ACTIVE);

        given(messageRepo.findByIdForUpdate(MessageId.of(100L))).willReturn(Optional.of(msg));
        given(chatRoomRepo.findByIdForUpdateWrite(ChatRoomId.of(10L))).willReturn(Optional.of(chatRoom(10L, ChatRoomType.GAME)));
        given(messageRepo.save(any())).willAnswer(inv -> inv.getArgument(0));

        MessageDeleteResponse result = messageService.delete(100L, 1L);

        assertThat(result.messageId()).isEqualTo(100L);
        assertThat(result.roomId()).isEqualTo(10L);
        assertThat(result.status()).isEqualTo("DELETED");
    }

    /**
     * 존재하지 않는 메시지 삭제 시 NOT_FOUND 예외.
     * Mock: findByIdForUpdate → Optional.empty
     */
    @Test
    @DisplayName("존재하지 않는 메시지를 삭제하면 예외가 발생한다")
    void delete_메시지없음_예외() {
        stubClock();
        given(messageRepo.findByIdForUpdate(MessageId.of(999L))).willReturn(Optional.empty());

        assertThatThrownBy(() -> messageService.delete(999L, 1L))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * 이미 삭제된 메시지는 NOT_FOUND로 처리하여 삭제 여부를 외부에 노출하지 않는다.
     * 멱등성 없는 이중 삭제를 허용하면 DELETED 메시지에 추가 이벤트가 발행될 수 있음.
     * 실패 포인트: isDeleted() 체크 누락 시 삭제 이벤트 중복 발행.
     * Mock: findByIdForUpdate → DELETED 상태 메시지
     */
    @Test
    @DisplayName("이미 삭제된 메시지를 다시 삭제하면 예외가 발생한다")
    void delete_이미삭제됨_예외() {
        stubClock();
        Message deleted = message(100L, 10L, 1L, "삭제된 메시지", MessageStatus.DELETED);
        given(messageRepo.findByIdForUpdate(MessageId.of(100L))).willReturn(Optional.of(deleted));

        assertThatThrownBy(() -> messageService.delete(100L, 1L))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * 다른 사람의 메시지를 삭제하면 FORBIDDEN 예외.
     * 메시지 소유권 검증은 데이터 프라이버시의 기본 요건.
     * Mock: findByIdForUpdate → senderId=1L 메시지, 요청자=2L
     */
    @Test
    @DisplayName("자신이 작성하지 않은 메시지를 삭제하면 예외가 발생한다")
    void delete_다른사람메시지_예외() {
        stubClock();
        Message msg = message(100L, 10L, 1L, "다른 사람 메시지", MessageStatus.ACTIVE);
        given(messageRepo.findByIdForUpdate(MessageId.of(100L))).willReturn(Optional.of(msg));

        assertThatThrownBy(() -> messageService.delete(100L, 2L))
                .isInstanceOf(BusinessException.class);
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

    /**
     * DELETED 방의 메시지를 조회하면 NOT_FOUND 예외.
     * 삭제된 방의 메시지 조회 허용은 데이터 접근 제어 위반.
     * Mock: findByIdForUpdateRead → DELETED 방 반환
     */
    @Test
    @DisplayName("DELETED 채팅방 메시지 조회 시 예외가 발생한다")
    void getMessages_DELETED방_예외() {
        ChatRoom deleted = ChatRoom.restore(
                ChatRoomId.of(10L), ChatRoomName.of("방"), ChatRoomType.GAME, null,
                GameId.of(5L), NOW, NOW, ChatRoomStatus.DELETED, MemberId.of(1L));
        given(chatRoomRepo.findByIdForUpdateRead(ChatRoomId.of(10L))).willReturn(Optional.of(deleted));

        assertThatThrownBy(() -> messageService.getMessages(
                new MessagePageNationRequest(null, 20), 10L, 1L))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * 익명 사용자(memberId=null)는 DIRECT 방 메시지에 접근할 수 없다.
     * DIRECT 방의 메시지는 참여 멤버만 볼 수 있어야 한다.
     * 실패 포인트: null 체크 없이 isMember를 true로 가정하면 인가 우회 가능.
     * Mock: findByIdForUpdateRead → DIRECT 방, memberId=null
     */
    @Test
    @DisplayName("익명 사용자가 DIRECT 채팅방 메시지 조회 시 예외가 발생한다")
    void getMessages_익명사용자_DIRECT방_예외() {
        ChatRoom directRoom = ChatRoom.restore(
                ChatRoomId.of(20L), ChatRoomName.of("DM"), ChatRoomType.DIRECT, null,
                null, NOW, NOW, ChatRoomStatus.ACTIVE, MemberId.of(1L));
        given(chatRoomRepo.findByIdForUpdateRead(ChatRoomId.of(20L))).willReturn(Optional.of(directRoom));

        assertThatThrownBy(() -> messageService.getMessages(
                new MessagePageNationRequest(null, 20), 20L, null))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * DIRECT 방 멤버가 아닌 인증 사용자도 메시지를 조회할 수 없다.
     * existsJoinedByRoomAndMember=false → isMember=false → FORBIDDEN 예외.
     * Mock: existsJoinedByRoomAndMember → false (방 멤버 아님 시뮬레이션)
     */
    @Test
    @DisplayName("DIRECT 채팅방 멤버가 아닌 사용자가 메시지 조회 시 예외가 발생한다")
    void getMessages_비멤버_DIRECT방_예외() {
        ChatRoom directRoom = ChatRoom.restore(
                ChatRoomId.of(20L), ChatRoomName.of("DM"), ChatRoomType.DIRECT, null,
                null, NOW, NOW, ChatRoomStatus.ACTIVE, MemberId.of(1L));
        given(chatRoomRepo.findByIdForUpdateRead(ChatRoomId.of(20L))).willReturn(Optional.of(directRoom));
        given(chatRoomMemberRepo.existsJoinedByRoomAndMember(ChatRoomId.of(20L), MemberId.of(99L))).willReturn(false);

        assertThatThrownBy(() -> messageService.getMessages(
                new MessagePageNationRequest(null, 20), 20L, 99L))
                .isInstanceOf(BusinessException.class);
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

        org.mockito.Mockito.verify(readCache).put(
                eq(ChatRoomId.of(10L)), eq(MemberId.of(1L)), eq(MessageId.of(100L)));
    }

    /**
     * CAS 비교(tonumber(ARGV[1]) > tonumber(c))는 Lua 스크립트 내부에서 수행된다.
     * Java 코드는 비교 결과와 무관하게 항상 execute를 호출하고 반환값(0=스킵, 1=갱신)을 무시한다.
     * 이 테스트는 스크립트가 0을 반환해도 예외 없이 종료되고, execute에 올바른 값이 전달됨을 검증한다.
     * 실패 포인트: Java 측에 반환값 체크 로직이 잘못 추가되면 CAS 스킵 시 예외가 발생할 수 있음.
     * (Lua 내부의 실제 분기는 실제 Redis가 필요한 통합 테스트에서 검증)
     */
    @Test
    @DisplayName("DIRECT 방이면 readCache.put이 올바른 인자로 호출된다")
    void read_DIRECT방_readCache_put호출() {
        ChatRoom directRoom = ChatRoom.restore(
                ChatRoomId.of(10L), ChatRoomName.of("DM"), ChatRoomType.DIRECT, null,
                null, NOW, NOW, ChatRoomStatus.ACTIVE, MemberId.of(1L));
        given(chatRoomRepo.findById(ChatRoomId.of(10L))).willReturn(Optional.of(directRoom));

        messageService.read(10L, 1L, 50L, true);

        org.mockito.Mockito.verify(readCache).put(
                eq(ChatRoomId.of(10L)), eq(MemberId.of(1L)), eq(MessageId.of(50L)));
    }

    @Test
    @DisplayName("lastReadMessageId가 0 이하이면 BusinessException이 발생한다")
    void read_lastReadMessageId_0이하_예외() {
        assertThatThrownBy(() -> messageService.read(10L, 1L, 0L, true))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> messageService.read(10L, 1L, -1L, true))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> messageService.read(10L, 1L, null, true))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("needDirectCheck=true이고 GAME 방이면 Redis 기록을 건너뛴다")
    void read_GAME방_Redis스킵() {
        ChatRoom gameRoom = chatRoom(10L, ChatRoomType.GAME);
        given(chatRoomRepo.findById(ChatRoomId.of(10L))).willReturn(Optional.of(gameRoom));

        messageService.read(10L, 1L, 100L, true);

        verifyNoInteractions(readCache);
    }

    // ──────────────────────── 픽스처 헬퍼 ────────────────────────

    private void stubClock() {
        lenient().when(clock.instant()).thenReturn(NOW_INSTANT);
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

    private ChatRoomMember memberWithStatus(Long roomId, Long memberId, MemberStatus status) {
        return ChatRoomMember.restore(
                1L, ChatRoomId.of(roomId), MemberId.of(memberId),
                status, true, NOW, NOW, null
        );
    }
}
