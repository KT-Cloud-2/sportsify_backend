package com.sportsify.chat.application.chatRoomMember.service;

import com.sportsify.chat.application.chatRoomMember.dto.ChatRoomMemberResponse;
import com.sportsify.chat.domain.model.chatRoom.*;
import com.sportsify.chat.domain.model.chatRoomMember.ChatRoomMember;
import com.sportsify.chat.domain.model.chatRoomMember.MemberStatus;
import com.sportsify.chat.domain.repository.ChatRoomMemberRepo;
import com.sportsify.chat.domain.repository.ChatRoomRepo;
import com.sportsify.chat.infrastructure.persistence.lock.AdvisoryLockAdaptor;
import com.sportsify.chat.infrastructure.persistence.lock.AdvisoryLockKeys;
import com.sportsify.common.exception.BusinessException;
import com.sportsify.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ChatRoomMemberService {
    private final ChatRoomRepo chatRoomRepo;
    private final ChatRoomMemberRepo chatRoomMemberRepo;
    private final Clock clock;
    private final AdvisoryLockAdaptor advisoryLockAdaptor;

    /**
     * 채팅방 입장
     */
    @Transactional
    public ChatRoomMemberResponse join(Long roomId, Long memberId) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<MemberStatus> accessStatuses = List.of(MemberStatus.JOINED, MemberStatus.INVITED, MemberStatus.BANNED, MemberStatus.LEFT);
        ChatRoom room = findChatRoomWithId(roomId);
        MemberId id = MemberId.of(memberId);
        String locKey = AdvisoryLockKeys.directRoomCreationForMemberJoin(room.getId(), id);
        if (!advisoryLockAdaptor.tryAcquireXactLock(locKey)) {
            throw new BusinessException(ErrorCode.CONFLICT, "Already processing join request");
        }
        Optional<ChatRoomMember> memberOpt = findMemberWithStatus(room.getId(), memberId, accessStatuses);

        if (memberOpt.isEmpty()) {
            if (room.getType() == ChatRoomType.DIRECT) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "Cannot find this member: " + memberId);
            }
            ChatRoomMember newMember = ChatRoomMember.newJoin(room.getId(), id, now);
            try {
                return ChatRoomMemberResponse.from(chatRoomMemberRepo.saveAndFlush(newMember));
            } catch (DataIntegrityViolationException e) {
                throw new BusinessException(ErrorCode.CONFLICT, "Already exists in this room: " + roomId + " member: " + memberId);
            }

        }

        ChatRoomMember member = memberOpt.get();
        return switch (member.getStatus()) {
            case JOINED -> throw new BusinessException(ErrorCode.CONFLICT, "Already joined room");
            case BANNED -> throw new BusinessException(ErrorCode.FORBIDDEN, "This user is banned");
            default -> {
                member.accept(now);
                yield ChatRoomMemberResponse.from(chatRoomMemberRepo.save(member));
            }
        };
    }

    /**
     * 채팅방 퇴장
     */
    @Transactional
    public ChatRoomMemberResponse leave(Long roomId, Long memberId) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<MemberStatus> accessStatuses = List.of(MemberStatus.JOINED, MemberStatus.LEFT, MemberStatus.BANNED);
        ChatRoomMember member = findMemberWithStatus(ChatRoomId.of(roomId), memberId, accessStatuses)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Cannot find this user: " + memberId));
        return switch (member.getStatus()) {
            case LEFT -> throw new BusinessException(ErrorCode.CONFLICT, "Already left room");
            case BANNED -> throw new BusinessException(ErrorCode.FORBIDDEN, "This user is banned");
            default -> {
                member.leave(now);
                yield ChatRoomMemberResponse.from(chatRoomMemberRepo.save(member));
            }
        };
    }


    /**
     * 채팅방 초대
     */
    @Transactional
    public ChatRoomMemberResponse invite(Long roomId, Long requesterId, Long inviteeId) {
        if (requesterId.equals(inviteeId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Cannot invite yourself");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        ChatRoom room = findChatRoomWithId(roomId);
        if (room.getType() == ChatRoomType.DIRECT) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Cannot invite to DIRECT room");
        }
        if (!chatRoomMemberRepo.existsJoinedByRoomAndMember(room.getId(), MemberId.of(requesterId))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only joined members can invite");
        }

        Optional<ChatRoomMember> inviteeOpt = findMemberWithStatus(room.getId(), inviteeId,
                List.of(MemberStatus.JOINED, MemberStatus.INVITED, MemberStatus.BANNED, MemberStatus.LEFT));

        if (inviteeOpt.isPresent()) {
            ChatRoomMember member = inviteeOpt.get();
            return switch (member.getStatus()) {
                case JOINED -> throw new BusinessException(ErrorCode.CONFLICT, "Already joined member: " + inviteeId);
                case INVITED -> throw new BusinessException(ErrorCode.CONFLICT, "Already invited member: " + inviteeId);
                case BANNED ->
                        throw new BusinessException(ErrorCode.FORBIDDEN, "Banned member cannot be invited: " + inviteeId);
                default -> {
                    member.changeStatusToInvite(now);
                    yield ChatRoomMemberResponse.from(chatRoomMemberRepo.save(member));
                }
            };
        }

        ChatRoomMember newMember = ChatRoomMember.newInvited(room.getId(), MemberId.of(inviteeId), now);
        try {
            return ChatRoomMemberResponse.from(chatRoomMemberRepo.saveAndFlush(newMember));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.CONFLICT, "Already exists in this room: " + roomId);
        }

    }

    /**
     * 채팅방 멤버 BAN
     */
    @Transactional
    public ChatRoomMemberResponse ban(Long roomId, Long requesterId, Long targetId) {
        if (requesterId.equals(targetId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Cannot ban yourself");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        ChatRoom room = findChatRoomWithId(roomId);

        if (!room.getCreatedBy().equals(MemberId.of(requesterId))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only room creator can ban members");
        }

        ChatRoomMember target = findMemberWithStatus(room.getId(), targetId,
                List.of(MemberStatus.JOINED, MemberStatus.INVITED, MemberStatus.BANNED, MemberStatus.LEFT))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Cannot find this member: " + targetId));

        if (target.isBanned()) {
            throw new BusinessException(ErrorCode.CONFLICT, "Already banned member: " + targetId);
        }

        target.ban(now);
        return ChatRoomMemberResponse.from(chatRoomMemberRepo.save(target));
    }

    /**
     * 알림 설정 변경
     */
    @Transactional
    public ChatRoomMemberResponse changeNotification(Long roomId, Long memberId, boolean enabled) {
        LocalDateTime now = LocalDateTime.now(clock);
        ChatRoomMember member = findMemberWithStatus(ChatRoomId.of(roomId), memberId, List.of(MemberStatus.JOINED))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Cannot find this member: " + memberId));

        member.changeNotification(enabled, now);
        return ChatRoomMemberResponse.from(chatRoomMemberRepo.save(member));
    }

    /* -------------------- private functions -------------------- */

    private Optional<ChatRoomMember> findMemberWithStatus(ChatRoomId roomId, Long memberId, List<MemberStatus> statuses) {
        MemberId id = MemberId.of(memberId);
        return chatRoomMemberRepo.findByRoomAndMemberWithStatus(roomId, id, statuses.stream().map(MemberStatus::name).toList());

    }

    private ChatRoom findChatRoomWithId(Long roomId) {
        ChatRoomId chatroomId = ChatRoomId.of(roomId);
        Optional<ChatRoom> room = chatRoomRepo.findById(chatroomId);
        if (room.isEmpty() || room.get().getStatus().equals(ChatRoomStatus.DELETED)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Cannot find this room " + roomId);
        }
        return room.get();
    }


}
