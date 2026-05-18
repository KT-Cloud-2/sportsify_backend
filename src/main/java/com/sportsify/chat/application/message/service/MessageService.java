package com.sportsify.chat.application.message.service;

import com.sportsify.chat.application.message.config.RedisKeySchema;
import com.sportsify.chat.application.message.dto.*;
import com.sportsify.chat.domain.model.chatRoom.*;
import com.sportsify.chat.domain.model.chatRoomMember.MemberStatus;
import com.sportsify.chat.domain.model.message.Message;
import com.sportsify.chat.domain.model.message.MessageContent;
import com.sportsify.chat.domain.model.message.MessageId;
import com.sportsify.chat.domain.model.message.MessageType;
import com.sportsify.chat.domain.repository.ChatRoomMemberRepository;
import com.sportsify.chat.domain.repository.ChatRoomRepository;
import com.sportsify.chat.domain.repository.MessageRepository;
import com.sportsify.common.exception.BusinessException;
import com.sportsify.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {
    private static final DefaultRedisScript<Long> CAS_SCRIPT = new DefaultRedisScript<>(
            "local c = redis.call('GET', KEYS[1]) " +
                    "if c == false or tonumber(ARGV[1]) > tonumber(c) then " +
                    "  redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[2]) return 1 " +
                    "end return 0", Long.class
    );


    final private MessageRepository messageRepo;
    final private ChatRoomRepository chatRoomRepo;
    final private ChatRoomMemberRepository chatRoomMemberRepo;
    private final Clock clock;
    private final StringRedisTemplate redisTemplate;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 메시지 전송
     */
    @Transactional
    public MessageCreateResponse send(MessageCreateRequest request, Long memberId) {
        ChatRoomId chatRoomId = ChatRoomId.of(request.roomId());
        MemberId id = MemberId.of(memberId);
        MessageContent content = MessageContent.of(request.content());

        ChatRoomStatus chatRoomStatus = chatRoomRepo.findByIdForUpdateWrite(chatRoomId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Cannot find chat room: " + chatRoomId.value())).getStatus();

        switch (chatRoomStatus) {
            case DELETED -> throw new BusinessException(ErrorCode.NOT_FOUND, "Cannot find chat room: " + chatRoomId.value());
            case ARCHIVED -> throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "This room is archived : " + chatRoomId.value());
            case ACTIVE -> {
            }
            default -> throw new IllegalStateException("Unhandled status " + chatRoomStatus);
        }
        MemberStatus memberStatus = chatRoomMemberRepo.findByRoomAndMemberForUpdate(chatRoomId, id).orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "User : " + id.value() + " is not room member : " + chatRoomId.value())).getStatus();

        switch (memberStatus) {
            case BANNED -> throw new BusinessException(ErrorCode.FORBIDDEN, "Banned user: " + id.value());
            case DELETED, LEFT -> throw new BusinessException(ErrorCode.FORBIDDEN, "User is not room member: " + id.value());
            case INVITED -> throw new BusinessException(ErrorCode.FORBIDDEN, "User has not accepted invite: " + id.value());
            case JOINED -> {
            }
            default -> throw new IllegalStateException("Unhandled status " + memberStatus);
        }
        Message savedMessage = messageRepo.save(Message.send(chatRoomId, id, content, parseType(request.type()), Instant.now(clock), request.clientMessageId()));
        savedMessage.getEvents().forEach(eventPublisher::publishEvent);
        return MessageCreateResponse.from(savedMessage);
    }

    /**
     * 메시지 삭제
     */
    @Transactional
    public MessageDeleteResponse delete(Long messageId, Long memberId) {

        Message message = messageRepo.findByIdForUpdate(MessageId.of(messageId)).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Message not found " + messageId));
        if (message.isDeleted()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Message not found " + messageId);
        }
        MemberId id = MemberId.of(memberId);
        if (!message.getSenderId().equals(id)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Cannot delete other user's message");
        }
        Instant now = Instant.now(clock);
        message.softDelete(id, now);
        Message savedMessage = messageRepo.save(message);
        savedMessage.getEvents().forEach(eventPublisher::publishEvent);
        return MessageDeleteResponse.from(savedMessage);
    }

    /**
     * 채팅 이력 조회
     */
    @Transactional(readOnly = true)
    public MessageListResponse getHistory(MessagePageNationRequest request, Long roomId, Long memberId) {
        ChatRoomId chatRoomId = ChatRoomId.of(roomId);
        MemberId id = MemberId.of(memberId);
        List<Message> messages = messageRepo.findByRoomAndMemberBefore(chatRoomId, id, request.cursor(), request.limit() + 1);
        PageResult page = paginate(messages, request.limit());
        return new MessageListResponse(page.items().stream().map(MessageSummaryResponse::from).toList(), null, page.nextCursor(), page.hasNext(), page.items().size());

    }

    /**
     * 채팅방 메시지 조회
     */
    @Transactional
    public MessageListResponse getMessages(MessagePageNationRequest request, Long roomId, Long memberId) {
        ChatRoomId chatRoomId = ChatRoomId.of(roomId);
        MemberId id = memberId != null ? MemberId.of(memberId) : null;
        ChatRoom chatRoom = chatRoomRepo.findByIdForUpdateRead(chatRoomId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Cannot find room : " + chatRoomId.value()));
        ChatRoomStatus roomStatus = chatRoom.getStatus();
        if (roomStatus == ChatRoomStatus.DELETED) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Cannot find room : " + chatRoomId.value());
        }
        boolean isMember = id != null && chatRoomMemberRepo.existsJoinedByRoomAndMember(chatRoomId, id);
        boolean isDirectRoom = ChatRoomType.DIRECT == chatRoom.getType();
        if (isDirectRoom && !isMember) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "User is not room member : " + (id != null ? id.value() : "anonymous"));
        }
        List<Message> messages = messageRepo.findByRoomBefore(chatRoomId, request.cursor(), request.limit() + 1);

        PageResult page = paginate(messages, request.limit());

        Map<MemberId, MessageId> chatRoomMembersInfo = isDirectRoom ? chatRoomMemberRepo.findLastMessageIdsAndMemberIdsByRoomId(chatRoomId) : null;

        if (!page.items().isEmpty() && roomStatus == ChatRoomStatus.ACTIVE && isMember && isDirectRoom) {
            read(chatRoomId.value(), id.value(), page.items().getLast().getId().value(), false);
        }
        return new MessageListResponse(
                page.items().stream().map(MessageResponse::from).toList(),
                MessageMemberInfoSummaryResponse.of(chatRoomMembersInfo),
                page.nextCursor(),
                page.hasNext(),
                page.items().size());
    }


    public void read(Long roomId, Long memberId, Long lastReadMessageId, boolean needDirectCheck) {
        if (needDirectCheck) {
            boolean isDirect = chatRoomRepo.findById(ChatRoomId.of(roomId))
                    .map(r -> r.getType() == ChatRoomType.DIRECT)
                    .orElse(false);
            if (!isDirect) {
                return;
            }
        }
        String key = String.format(RedisKeySchema.LAST_READ_KEY_PREFIX, roomId, memberId);
        redisTemplate.execute(CAS_SCRIPT, List.of(key),
                String.valueOf(lastReadMessageId), String.valueOf(RedisKeySchema.LAST_READ_TTL_SECONDS));
    }



    /* -------------------- private functions -------------------- */


    private MessageType parseType(String type) {
        try {
            return MessageType.valueOf(type);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "type=" + type);
        }
    }

    private PageResult paginate(List<Message> messages, int limit) {
        boolean hasNext = messages.size() > limit;
        List<Message> items = hasNext ? messages.subList(0, limit) : messages;
        Long nextCursor = hasNext ? items.getLast().getId().value() : null;
        return new PageResult(items, nextCursor, hasNext);
    }

    private record PageResult(List<Message> items, Long nextCursor, boolean hasNext) {
    }
}
