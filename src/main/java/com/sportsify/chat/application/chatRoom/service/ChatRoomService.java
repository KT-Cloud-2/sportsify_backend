package com.sportsify.chat.application.chatRoom.service;

import com.sportsify.chat.application.chatRoom.dto.*;
import com.sportsify.chat.domain.model.chatRoom.*;
import com.sportsify.chat.domain.model.chatRoomMember.ChatRoomMember;
import com.sportsify.chat.domain.repository.ChatRoomMemberRepo;
import com.sportsify.chat.domain.repository.ChatRoomRepo;
import com.sportsify.chat.infrastructure.persistence.lock.AdvisoryLockAdaptor;
import com.sportsify.chat.infrastructure.persistence.lock.AdvisoryLockKeys;
import com.sportsify.common.exception.BusinessException;
import com.sportsify.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class ChatRoomService {

    private final ChatRoomRepo chatRoomRepo;
    private final ChatRoomMemberRepo chatRoomMemberRepo;
    private final Clock clock;
    private final AdvisoryLockAdaptor advisoryLockAdaptor;

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
                request.inviteeIds().stream().map(MemberId::of).filter(mId -> !creatorId.equals(mId))
                        .map(id -> ChatRoomMember.newInvited(room.getId(), id, now))
        ).toList();
        chatRoomMemberRepo.saveAll(members);
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

        return ChatRoomUpdateResponse.from(chatRoomRepo.save(room));
    }

    /**
     * 방 삭제
     */
    @Transactional
    public void delete(Long roomId, Long memberId) {
        LocalDateTime now = LocalDateTime.now(clock);
        MemberId requesterId = MemberId.of(memberId);
        ChatRoom room = findActiveRoomForUpdate(roomId);
        if (!requesterId.equals(room.getCreatedBy())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only room leader can delete room");
        }
        room.delete(now, requesterId);
        chatRoomRepo.save(room);
        chatRoomMemberRepo.leaveAllMembersByRoom(room.getId(), now);
    }

    /**
     * 내 채팅방 목록 조회
     */
    @Transactional(readOnly = true)
    public ChatRoomListResponse getMyRooms(ChatRoomGetRequest request, Long memberId) {
        ChatRoomType roomType = parseType(request.type());
        Long cursor = request.cursor();

        List<ChatRoomMember> memberships = chatRoomMemberRepo.findActiveByMember(MemberId.of(memberId));

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

        Map<ChatRoomId, Long> countMap = chatRoomMemberRepo.countActiveByRooms(paged.stream().map(ChatRoom::getId).toList());
        Map<ChatRoomId, ChatRoomMember> membershipMap = memberships.stream()
                .filter(ChatRoomMember::isJoined)
                .collect(Collectors.toMap(ChatRoomMember::getRoomId, m -> m));

        List<ChatRoomSummaryResponse> items = paged.stream()
                .map(r -> ChatRoomSummaryResponse.of(
                        r,
                        countMap.getOrDefault(r.getId(), 0L),
                        membershipMap.get(r.getId())
                ))
                .toList();

        return new ChatRoomListResponse(items, nextCursor, hasNext, items.size());
    }

    /**
     * 게임별 채팅방 조회
     */
    @Transactional(readOnly = true)
    public ChatRoomListResponse getRoomsByGameId(ChatRoomGetByGameRequest request, Long gameId) {
        List<ChatRoomResponse> chatRooms = chatRoomRepo.findActiveByGameId(GameId.of(gameId), request.cursor(), request.limit() + 1)
                .stream().map(ChatRoomResponse::from).toList();
        if (chatRooms.isEmpty()) {
            return new ChatRoomListResponse(List.of(), null, false, 0);
        }
        boolean hasNext = chatRooms.size() > request.limit();
        List<ChatRoomResponse> paged = hasNext ? chatRooms.subList(0, request.limit()) : chatRooms;
        Long nextCursor = hasNext ? paged.getLast().roomId() : null;
        return new ChatRoomListResponse(paged, nextCursor, hasNext, paged.size());
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
        return ChatRoomDetailResponse.from(room, currentParticipants, ChatRoomMemberStatusResponse.from(membership));
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
     * select for update lock
     */
    private ChatRoom findActiveRoomForUpdate(Long roomId) {
        ChatRoom room = chatRoomRepo.findByIdForUpdate(ChatRoomId.of(roomId))
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
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "type=" + type);
        }
    }
}
