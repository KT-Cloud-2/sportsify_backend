package com.sportsify.chat.presentation.chatRoomMember.controller;

import com.sportsify.chat.application.chatRoomMember.dto.ChatRoomMemberResponse;
import com.sportsify.chat.application.chatRoomMember.service.ChatRoomMemberService;
import com.sportsify.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat/rooms")
@RequiredArgsConstructor
public class ChatRoomMemberController {

    private final ChatRoomMemberService chatRoomMemberService;

    @PostMapping("/{roomId}/members/me")
    public ResponseEntity<ApiResponse<ChatRoomMemberResponse>> join(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long roomId
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(chatRoomMemberService.join(roomId, memberId)));
    }

    @DeleteMapping("/{roomId}/members/me")
    public ResponseEntity<ApiResponse<ChatRoomMemberResponse>> leave(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long roomId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(chatRoomMemberService.leave(roomId, memberId)));
    }

    @PostMapping("/{roomId}/members/{inviteeId}/invite")
    public ResponseEntity<ApiResponse<ChatRoomMemberResponse>> invite(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long roomId,
            @PathVariable Long inviteeId
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(chatRoomMemberService.invite(roomId, memberId, inviteeId)));
    }

    @PostMapping("/{roomId}/members/{targetId}/ban")
    public ResponseEntity<ApiResponse<ChatRoomMemberResponse>> ban(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long roomId,
            @PathVariable Long targetId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(chatRoomMemberService.ban(roomId, memberId, targetId)));
    }

    @PatchMapping("/{roomId}/members/me/notification")
    public ResponseEntity<ApiResponse<ChatRoomMemberResponse>> changeNotification(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long roomId,
            @RequestParam boolean enabled
    ) {
        return ResponseEntity.ok(ApiResponse.ok(chatRoomMemberService.changeNotification(roomId, memberId, enabled)));
    }
}
