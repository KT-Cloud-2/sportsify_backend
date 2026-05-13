package com.sportsify.chat.application.chatRoom.service;

import com.sportsify.chat.application.chatRoom.dto.*;
import com.sportsify.chat.domain.model.chatRoom.*;
import com.sportsify.chat.domain.model.chatRoomMember.ChatRoomMember;
import com.sportsify.chat.domain.model.message.Message;
import com.sportsify.chat.domain.repository.ChatRoomMemberRepository;
import com.sportsify.chat.domain.repository.ChatRoomRepository;
import com.sportsify.chat.domain.repository.MessageRepository;
import com.sportsify.chat.infrastructure.persistence.lock.AdvisoryLockAdaptor;
import com.sportsify.chat.infrastructure.persistence.lock.AdvisoryLockKeys;
import com.sportsify.common.exception.BusinessException;
import com.sportsify.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepo;
    private final ChatRoomMemberRepository chatRoomMemberRepo;
    private final Clock clock;
    private final AdvisoryLockAdaptor advisoryLockAdaptor;
    private final ApplicationEventPublisher eventPublisher;
    private final MessageRepository messageRepo;

    /**
     * 채팅방 생성
     */
    @Transactional
    public ChatRoomResponse create(CreateChatRoomRequest request, Long memberId) {
        LocalDateTime now = LocalDateTime.now(clock);
        ChatRoomType type = parseType(request.type());
        MemberId creatorId = MemberId.of(memberId);
        if (type == ChatRoomType.DIRECT) {
            guardDirectRoom(request, creatorId);
        }
        if (type == ChatRoomType.GAME && request.gameId() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "GAME type requires gameId");
        }
        GameId gameId = type == ChatRoomType.GAME ? GameId.of(request.gameId()) : null;
        ChatRoomName name = resolveName(request.name(), type);
        ChatRoom room = chatRoomRepo.save(ChatRoom.create(name, type, request.imageUrl(), gameId, creatorId, now));
        List<ChatRoomMember> members = Stream.concat(
                Stream.of(ChatRoomMember.newJoin(room.getId(), creatorId, now)),
                Optional.ofNullable(request.inviteeIds())
                        .orElse(List.of())
                        .stream()
                        .map(MemberId::of)
                        .filter(mId -> !creatorId.equals(mId))
                        .map(id -> ChatRoomMember.newInvited(room.getId(), creatorId, id, now))
        ).toList();
        chatRoomMemberRepo.saveAll(members);
        members.forEach(m -> m.getEvents().forEach(eventPublisher::publishEvent));
        return ChatRoomResponse.from(room);
    }

    /**
     * 방 column 변경(name, imageUrl)
     */
    @Transactional
    public ChatRoomUpdateResponse update(ChatRoomUpdateRequest request, Long roomId, Long memberId) {
        if (request.name() == null && request.imageUrl() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "At least one field required");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        MemberId requesterId = MemberId.of(memberId);

        ChatRoom room = findActiveRoom(roomId);
        if (!room.getCreatedBy().equals(requesterId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only room leader can change");
        }
        if (request.name() != null) {
            room.rename(ChatRoomName.of(request.name()), now, requesterId);
        }
        if (request.imageUrl() != null) {
            String imageUrl = request.imageUrl().isBlank() ? null : request.imageUrl();
            room.changeImage(imageUrl, now, requesterId);
        }

        ChatRoom saved = chatRoomRepo.save(room);
        saved.getEvents().forEach(eventPublisher::publishEvent);
        return ChatRoomUpdateResponse.from(saved);
    }

    /**
     * 채팅방 아카이브 (ACTIVE → ARCHIVED)
     */
    @Transactional
    public ChatRoomArchiveResponse archive(Long roomId, Long memberId) {
        LocalDateTime now = LocalDateTime.now(clock);
        MemberId requesterId = MemberId.of(memberId);
        ChatRoom room = findActiveRoomForUpdate(roomId);
        if (!requesterId.equals(room.getCreatedBy())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only room leader can archive room");
        }
        room.archive(now);
        ChatRoom saved = chatRoomRepo.save(room);
        saved.getEvents().forEach(eventPublisher::publishEvent);
        return ChatRoomArchiveResponse.from(saved);
    }

    /**
     * 채팅방 아카이브 복원 (ARCHIVED → ACTIVE)
     */
    @Transactional
    public ChatRoomArchiveResponse unarchive(Long roomId, Long memberId) {
        LocalDateTime now = LocalDateTime.now(clock);
        MemberId requesterId = MemberId.of(memberId);
        ChatRoom room = findNonDeletedRoomForUpdate(roomId);
        if (!requesterId.equals(room.getCreatedBy())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only room leader can unarchive room");
        }
        room.unarchive(now);
        ChatRoom saved = chatRoomRepo.save(room);
        saved.getEvents().forEach(eventPublisher::publishEvent);
        return ChatRoomArchiveResponse.from(saved);
    }

    /**
     * 방 삭제
     */
    @Transactional
    public void delete(Long roomId, Long memberId) {
        LocalDateTime now = LocalDateTime.now(clock);
        MemberId requesterId = MemberId.of(memberId);
        ChatRoom room = findNonDeletedRoomForUpdate(roomId);
        if (!requesterId.equals(room.getCreatedBy())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only room leader can delete room");
        }
        room.delete(now, requesterId);
        ChatRoom saved = chatRoomRepo.save(room);
        saved.getEvents().forEach(eventPublisher::publishEvent);
        chatRoomMemberRepo.leaveAllMembersByRoom(room.getId(), now);
    }

    /**
     * 내 채팅방 목록 조회
     */
    @Transactional(readOnly = true)
    public ChatRoomListResponse getMyRooms(ChatRoomGetRequest request, Long memberId) {
        ChatRoomType roomType = parseType(request.type());
        Long cursor = request.cursor();
        MemberId id = MemberId.of(memberId);

        List<ChatRoomMember> memberships = chatRoomMemberRepo.findActiveByMember(id);

        List<ChatRoomId> roomIds = memberships.stream()
                .filter(ChatRoomMember::isJoined)
                .map(ChatRoomMember::getRoomId)
                .toList();
        if (roomIds.isEmpty()) {
            return new ChatRoomListResponse(List.of(), null, false, 0);
        }
        List<ChatRoom> rooms = chatRoomRepo.findActiveByRoomIds(roomIds, roomType, cursor, request.limit() + 1);
        boolean hasNext = rooms.size() > request.limit();
        List<ChatRoom> paged = hasNext ? rooms.subList(0, request.limit()) : rooms;
        Long nextCursor = hasNext ? paged.getLast().getId().value() : null;

        List<ChatRoomId> pagedIds = paged.stream().map(ChatRoom::getId).toList();
        Map<ChatRoomId, ChatRoomMember> membershipMap = memberships.stream()
                .collect(Collectors.toMap(ChatRoomMember::getRoomId, m -> m));
        Map<ChatRoomId, Message> lastMessages = messageRepo.findLastestByRooms(pagedIds)
                .stream()
                .collect(Collectors.toMap(Message::getRoomId, m -> m));

        Map<ChatRoomId, Long> countMap = chatRoomMemberRepo.countActiveByRooms(pagedIds);
        Map<ChatRoomId, Long> lastReadMap = new HashMap<>();
        pagedIds.forEach(roomId -> {
            Long lastReadId = membershipMap.get(roomId).getLastReadMessageId();
            lastReadMap.put(roomId, lastReadId != null ? lastReadId : 0L);
        });
        Map<ChatRoomId, Long> unreadCountMap = messageRepo.countUnreadByRooms(lastReadMap);

        List<ChatRoomSummaryResponse> items = paged.stream()
                .map(r -> ChatRoomSummaryResponse.of(
                        r,
                        countMap.getOrDefault(r.getId(), 0L),
                        membershipMap.get(r.getId()),
                        ChatMessageResponse.of(lastMessages.get(r.getId())),
                        unreadCountMap.getOrDefault(r.getId(), 0L)
                ))
                .toList();

        return new ChatRoomListResponse(items, nextCursor, hasNext, items.size());
    }

    /**
     * 게임별 채팅방 조회
     */
    @Transactional(readOnly = true)
    public ChatRoomListResponse getRoomsByGameId(ChatRoomGetByGameRequest request, Long gameId) {
        List<ChatRoom> chatRooms = chatRoomRepo.findActiveByGameId(GameId.of(gameId), request.cursor(), request.limit() + 1);
        if (chatRooms.isEmpty()) {
            return new ChatRoomListResponse(List.of(), null, false, 0);
        }
        boolean hasNext = chatRooms.size() > request.limit();
        List<ChatRoom> paged = hasNext ? chatRooms.subList(0, request.limit()) : chatRooms;
        Map<ChatRoomId, Long> participantCounts = getParticipantCounts(paged);
        Long nextCursor = hasNext ? paged.getLast().getId().value() : null;
        List<ChatRoomGetByGameResponse> response = paged.stream().
                map(room -> ChatRoomGetByGameResponse.from(room, participantCounts.getOrDefault(room.getId(), 0L))).toList();
        return new ChatRoomListResponse(response, nextCursor, hasNext, paged.size());
    }

    /**
     * 채팅방 상세 조회
     */
    @Transactional(readOnly = true)
    public ChatRoomDetailResponse getRoomDetail(Long roomId, Long memberId) {
        ChatRoom room = findActiveRoom(roomId);
        if (room.getType() == ChatRoomType.DIRECT && memberId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Detail DIRECT room search need memberId");
        }
        long currentParticipants = chatRoomMemberRepo.countActiveByRoom(room.getId());
        Optional<ChatRoomMember> membership = memberId != null
                ? chatRoomMemberRepo.findByRoomAndMember(room.getId(), MemberId.of(memberId))
                : Optional.empty();
        ChatRoomMemberStatusResponse chatRoomMemberStatusResponse = membership.map(ChatRoomMemberStatusResponse::from).orElse(null);
        return ChatRoomDetailResponse.from(room, currentParticipants, chatRoomMemberStatusResponse);
    }




    /* -------------------- private functions -------------------- */

    /**
     * DIRECT ROOM 중복 체크 및 LOCK
     */
    private void guardDirectRoom(CreateChatRoomRequest request, MemberId creatorId) {
        if (request.inviteeIds().size() != 1) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "DIRECT chatRoom requires exactly one participant.");
        }
        MemberId otherId = MemberId.of(request.inviteeIds().getFirst());
        if (creatorId.equals(otherId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Tried to make same user private room");
        }
        String lockKey = AdvisoryLockKeys.directRoomCreationForDm(creatorId, otherId);
        if (!advisoryLockAdaptor.tryAcquireXactLock(lockKey)) {
            throw new BusinessException(ErrorCode.CONFLICT, "Already making DIRECT chatRoom between " + creatorId.value() + " and " + otherId.value());
        }
        chatRoomRepo.existByCreatorIdAndInviteId(creatorId.value(), otherId.value()).ifPresent(id -> {
            throw new BusinessException(ErrorCode.CONFLICT, "Cannot create a direct chat room because it already exists. roomId: " + id);
        });
    }

    /**
     * chatRoom name 자동 생성
     */
    private ChatRoomName resolveName(String name, ChatRoomType type) {
        if (type == ChatRoomType.GAME) {
            if (name == null || name.isBlank()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "GAME type requires name");
            }
            return ChatRoomName.of(name);
        }
        return ChatRoomName.of(name != null && !name.isBlank() ? name : "DM");
    }

    /**
     * id 값으로 active 상태인 room find
     */
    private ChatRoom findActiveRoom(Long roomId) {
        ChatRoom room = chatRoomRepo.findById(ChatRoomId.of(roomId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "roomId=" + roomId));
        if (room.getStatus() == ChatRoomStatus.DELETED) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "roomId=" + roomId);
        }
        return room;
    }

    /**
     * select for update lock (ACTIVE only)
     */
    private ChatRoom findActiveRoomForUpdate(Long roomId) {
        ChatRoom room = chatRoomRepo.findByIdForUpdateWrite(ChatRoomId.of(roomId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "roomId=" + roomId));
        if (room.getStatus() != ChatRoomStatus.ACTIVE && room.getStatus() != ChatRoomStatus.EMPTY) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "roomId=" + roomId);
        }
        return room;
    }

    /**
     * select for update lock (DELETED 제외)
     */
    private ChatRoom findNonDeletedRoomForUpdate(Long roomId) {
        ChatRoom room = chatRoomRepo.findByIdForUpdateWrite(ChatRoomId.of(roomId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "roomId=" + roomId));
        if (room.getStatus() == ChatRoomStatus.DELETED) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "roomId=" + roomId);
        }
        return room;
    }

    /**
     * room type으로 타입 변경
     */
    private ChatRoomType parseType(String type) {
        try {
            return ChatRoomType.valueOf(type);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "type=" + type);
        }
    }

    /**
     * room 참여자 일괄 검색
     */
    private Map<ChatRoomId, Long> getParticipantCounts(List<ChatRoom> rooms) {
        List<ChatRoomId> roomIds = rooms.stream().map(ChatRoom::getId).toList();
        return chatRoomMemberRepo.countActiveByRooms(roomIds); // IN 절을 사용하는 쿼리로 구현
    }
}
