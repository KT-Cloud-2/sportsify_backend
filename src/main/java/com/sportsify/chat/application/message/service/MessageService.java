package com.sportsify.chat.application.message.service;

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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MessageService {
    final private MessageRepository messageRepo;
    final private ChatRoomRepository chatRoomRepo;
    final private ChatRoomMemberRepository chatRoomMemberRepo;
    private final Clock clock;
    private final ApplicationEventPublisher applicationEventPublisher;


    /**
     * 메시지 전송
     */
    @Transactional
    public MessageCreateResponse send(MessageCreateRequest request, Long memberId) {
        ChatRoomId chatRoomId = ChatRoomId.of(request.roomId());
        MemberId id = MemberId.of(memberId);
        ChatRoomStatus chatRoomStatus = chatRoomRepo.findByIdForUpdateWrite(chatRoomId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Cannot find chat room: " + chatRoomId.value())).getStatus();

        switch (chatRoomStatus) {
            case DELETED ->
                    throw new BusinessException(ErrorCode.NOT_FOUND, "Cannot find chat room: " + chatRoomId.value());
            case ARCHIVED ->
                    throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "This room is archived : " + chatRoomId.value());
            case ACTIVE -> {
            }
            default -> throw new IllegalStateException("Unhandled status " + chatRoomStatus);
        }
        MemberStatus memberStatus = chatRoomMemberRepo.findByRoomAndMemberForUpdate(chatRoomId, id).orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "User : " + id.value() + " is not room member : " + chatRoomId.value())).getStatus();

        switch (memberStatus) {
            case BANNED -> throw new BusinessException(ErrorCode.FORBIDDEN, "Banned user: " + id.value());
            case DELETED, LEFT ->
                    throw new BusinessException(ErrorCode.FORBIDDEN, "User is not room member: " + id.value());
            case INVITED ->
                    throw new BusinessException(ErrorCode.FORBIDDEN, "User has not accepted invite: " + id.value());
            case JOINED -> {
            }
            default -> throw new IllegalStateException("Unhandled status " + memberStatus);
        }
        Message savedMessage = messageRepo.save(Message.send(chatRoomId, id, MessageContent.of(request.content()), parseType(request.type()), LocalDateTime.now(clock)));
        savedMessage.pullDomainEvents().forEach(applicationEventPublisher::publishEvent);
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
        LocalDateTime now = LocalDateTime.now(clock);
        message.softDelete(id, now);
        Message savedMessage = messageRepo.save(message);
        savedMessage.pullDomainEvents().forEach(applicationEventPublisher::publishEvent);
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
        return new MessageListResponse(page.items().stream().map(MessageSummaryResponse::from).toList(), page.nextCursor(), page.hasNext(), page.items().size());

    }

    /**
     * 채팅방 메시지 조회
     */
    @Transactional
    public MessageListResponse getMessages(MessagePageNationRequest request, Long roomId, Long memberId) {
        ChatRoomId chatRoomId = ChatRoomId.of(roomId);
        MemberId id = MemberId.of(memberId);
        ChatRoom chatRoom = chatRoomRepo.findByIdForUpdateRead(chatRoomId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Cannot find room : " + chatRoomId.value()));
        ChatRoomStatus roomStatus = chatRoom.getStatus();
        if (roomStatus == ChatRoomStatus.DELETED) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Cannot find room : " + chatRoomId.value());
        }
        boolean isMember = chatRoomMemberRepo.existsJoinedByRoomAndMember(chatRoomId, id);
        if (chatRoom.getType() == ChatRoomType.DIRECT && !isMember) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "User is not room member : " + id.value());
        }
        List<Message> messages = messageRepo.findByRoomBefore(chatRoomId, request.cursor(), request.limit() + 1);

        PageResult page = paginate(messages, request.limit());

        if (!page.items().isEmpty() && roomStatus == ChatRoomStatus.ACTIVE && isMember) {
            LocalDateTime now = LocalDateTime.now(clock);
            chatRoomMemberRepo.updateLastReadMessageIfGreater(
                    chatRoomId, id,
                    page.items().getLast().getId(), now);
        }
        return new MessageListResponse(page.items().stream().map(MessageResponse::from).toList(), page.nextCursor(), page.hasNext(), page.items().size());
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
