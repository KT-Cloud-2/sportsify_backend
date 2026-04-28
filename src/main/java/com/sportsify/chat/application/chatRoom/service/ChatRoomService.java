package com.sportsify.chat.application.chatRoom.service;

import com.sportsify.chat.application.chatRoom.ChatRoomSummaryResponse;
import com.sportsify.chat.application.chatRoom.ChatRoomUpdateResponse;
import com.sportsify.chat.application.chatRoom.UpdateChatRoomRequest;
import com.sportsify.chat.application.chatRoom.dto.ChatRoomCreateResponseDto;
import com.sportsify.chat.application.chatRoom.dto.CreateChatRoomRequestDto;
import com.sportsify.chat.domain.model.chatRoom.*;
import com.sportsify.chat.domain.model.chatRoomMember.ChatRoomMember;
import com.sportsify.chat.domain.repository.ChatRoomMemberRepo;
import com.sportsify.chat.domain.repository.ChatRoomRepo;
import com.sportsify.common.exception.BusinessException;
import com.sportsify.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatRoomService {

    private final ChatRoomRepo chatRoomRepo;
    private final ChatRoomMemberRepo chatRoomMemberRepo;
    private final Clock clock;

    /**
     * 채팅방 생성 service
     */
    @Transactional
    public ChatRoomCreateResponseDto create(CreateChatRoomRequestDto request, Long memberId) {

        LocalDateTime now = LocalDateTime.now(clock);
        ChatRoomType type = parseType(request.type());
        MemberId creatorId = MemberId.of(memberId);
        GameId gameId = type == ChatRoomType.GAME ? GameId.of(request.gameId()) : null;
        ChatRoomName name = resolveName(request.name(), type);
        if(type == ChatRoomType.DIRECT){
            if(chatRoomRepo.fin)
        }
        ChatRoom room = ChatRoom.create(name, type, request.imageUrl(), gameId, creatorId, now);
        room = chatRoomRepo.save(room);

        chatRoomMemberRepo.save(ChatRoomMember.newJoin(room.getId(), creatorId, now));

        return ChatRoomCreateResponseDto.from(room);
    }

    public ChatRoomDetailResponse getDetail(Long roomId, Long memberId) {
        ChatRoom room = findActiveRoom(roomId);
        long currentParticipants = chatRoomMemberRepo.countActiveByRoom(room.getId());

        ChatRoomMember membership = null;
        if (memberId != null) {
            membership = chatRoomMemberRepo
                    .findByRoomAndMember(room.getId(), MemberId.of(memberId))
                    .orElse(null);
        }

        return ChatRoomDetailResponse.of(room, currentParticipants, membership);
    }

    public ChatRoomListResponse getMyRooms(Long memberId, String type, Long cursor, int limit) {
        int safeLimit = Math.min(limit, 100);
        ChatRoomType roomType = parseType(type);

        List<ChatRoomMember> memberships = chatRoomMemberRepo.findActiveByMember(MemberId.of(memberId));

        List<ChatRoom> rooms = memberships.stream()
                .filter(ChatRoomMember::isJoined)
                .map(m -> chatRoomRepo.findById(m.getRoomId()).orElse(null))
                .filter(r -> r != null
                        && r.getType() == roomType
                        && r.getStatus() == ChatRoomStatus.ACTIVE
                        && (cursor == null || r.getId().value() < cursor))
                .sorted(Comparator.comparingLong((ChatRoom r) -> r.getId().value()).reversed())
                .collect(Collectors.toList());

        boolean hasNext = rooms.size() > safeLimit;
        if (hasNext) rooms = rooms.subList(0, safeLimit);
        Long nextCursor = hasNext ? rooms.get(rooms.size() - 1).getId().value() : null;

        List<ChatRoomSummaryResponse> items = rooms.stream()
                .map(r -> {
                    long count = chatRoomMemberRepo.countActiveByRoom(r.getId());
                    ChatRoomMember m = memberships.stream()
                            .filter(mb -> mb.getRoomId().equals(r.getId()))
                            .findFirst().orElse(null);
                    return ChatRoomSummaryResponse.of(r, count, m);
                })
                .toList();

        return new ChatRoomListResponse(items, nextCursor, hasNext, items.size());
    }

    public ChatRoomListResponse getRoomsByGame(Long gameId, Long cursor, int limit) {
        int safeLimit = Math.min(limit, 100);

        List<ChatRoom> rooms = chatRoomRepo.findByGameId(gameId).stream()
                .filter(r -> r.getStatus() == ChatRoomStatus.ACTIVE
                        && (cursor == null || r.getId().value() > cursor))
                .collect(Collectors.toList());

        boolean hasNext = rooms.size() > safeLimit;
        if (hasNext) rooms = rooms.subList(0, safeLimit);
        Long nextCursor = hasNext ? rooms.get(rooms.size() - 1).getId().value() : null;

        List<ChatRoomSummaryResponse> items = rooms.stream()
                .map(r -> ChatRoomSummaryResponse.of(r, chatRoomMemberRepo.countActiveByRoom(r.getId()), null))
                .toList();

        return new ChatRoomListResponse(items, nextCursor, hasNext, items.size());
    }

    @Transactional
    public ChatRoomUpdateResponse update(Long roomId, UpdateChatRoomRequest request, Long memberId) {
        LocalDateTime now = LocalDateTime.now();
        MemberId requesterId = MemberId.of(memberId);

        ChatRoom room = findActiveRoom(roomId);

        if (request.getTitle() != null) {
            room.rename(ChatRoomName.of(request.getTitle()), now, requesterId);
        }
        if (request.getImageUrl() != null) {
            String imageUrl = request.getImageUrl().isBlank() ? null : request.getImageUrl();
            room.changeImage(imageUrl, now, requesterId);
        }

        return ChatRoomUpdateResponse.from(chatRoomRepo.save(room));
    }

    private ChatRoom findActiveRoom(Long roomId) {
        ChatRoom room = chatRoomRepo.findById(ChatRoomId.of(roomId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "roomId=" + roomId));
        if (room.getStatus() == ChatRoomStatus.DELETED) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "roomId=" + roomId);
        }
        return room;
    }

    private ChatRoomType parseType(String type) {
        try {
            return ChatRoomType.valueOf(type);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "type=" + type);
        }
    }

    private ChatRoomName resolveName(String name, ChatRoomType type) {
        if (type == ChatRoomType.GAME) {
            if (name == null || name.isBlank()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "GAME type requires name");
            }
            return ChatRoomName.of(name);
        }
        return ChatRoomName.of(name != null && !name.isBlank() ? name : "DM");
    }
}
