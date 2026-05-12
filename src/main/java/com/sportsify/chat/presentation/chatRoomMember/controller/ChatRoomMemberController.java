package com.sportsify.chat.presentation.chatRoomMember.controller;

import com.sportsify.chat.application.chatRoomMember.dto.ChatRoomMemberResponse;
import com.sportsify.chat.application.chatRoomMember.service.ChatRoomMemberService;
import com.sportsify.chat.infrastructure.api.ChatRoomMemberApi;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat/rooms")
@RequiredArgsConstructor
public class ChatRoomMemberController implements ChatRoomMemberApi {

    private final ChatRoomMemberService chatRoomMemberService;

    /**
     * 5-9. 채팅방 입장
     *
     * @param memberId
     * @param roomId
     * @return ResponseEntity<CommonResponse < ChatRoomMemberResponse>>
     */
    @PostMapping("/{roomId}/join")
    public ResponseEntity<ChatRoomMemberResponse> join(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long roomId
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(chatRoomMemberService.join(roomId, memberId));
    }

    /**
     * 5-10. 채팅방 나가기
     *
     * @param memberId
     * @param roomId
     * @return ResponseEntity<CommonResponse < ChatRoomMemberResponse>>
     */
    @DeleteMapping("/{roomId}/invite")
    public ResponseEntity<ChatRoomMemberResponse> leave(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long roomId
    ) {
        return ResponseEntity.ok(chatRoomMemberService.leave(roomId, memberId));
    }

    /**
     * 5-11. 참여자 초대
     *
     * @param memberId
     * @param roomId
     * @param inviteeId
     * @return ResponseEntity<CommonResponse < ChatRoomMemberResponse>>
     */
    @PostMapping("/{roomId}/invite")
    public ResponseEntity<ChatRoomMemberResponse> invite(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long roomId,
            @RequestParam Long inviteeId
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(chatRoomMemberService.invite(roomId, memberId, inviteeId));
    }

    /**
     * 5-12. 알림 설정 변경
     *
     * @param memberId
     * @param roomId
     * @param enabled
     * @return ResponseEntity<CommonResponse < ChatRoomMemberResponse>>
     */
    @PatchMapping("/{roomId}/notification")
    public ResponseEntity<ChatRoomMemberResponse> changeNotification(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long roomId,
            @RequestParam boolean enabled
    ) {
        return ResponseEntity.ok(chatRoomMemberService.changeNotification(roomId, memberId, enabled));
    }

    /**
     * 5-13. 채팅방 멤버 ban
     *
     * @param memberId
     * @param roomId
     * @param targetId
     * @return ResponseEntity<CommonResponse < ChatRoomMemberResponse>>
     */
    @PostMapping("/{roomId}/ban")
    public ResponseEntity<ChatRoomMemberResponse> ban(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long roomId,
            @RequestParam Long targetId
    ) {
        return ResponseEntity.ok(chatRoomMemberService.ban(roomId, memberId, targetId));
    }


}
