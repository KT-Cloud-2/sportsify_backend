package com.sportsify.chat.presentation.chatRoom.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sportsify.chat.application.chatRoom.dto.*;
import com.sportsify.chat.application.chatRoom.service.ChatRoomService;
import com.sportsify.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat/rooms")
@RequiredArgsConstructor
public class ChatRoomController {

    private final ChatRoomService chatRoomService;

    /**
     * 5-1. 채팅방 생성
     */
    @PostMapping
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public ResponseEntity<ApiResponse<ChatRoomResponse>> create(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody CreateChatRoomRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(chatRoomService.create(request, memberId)));
    }

    /**
     * 5-2. 내 채팅방 목록 조회
     * [!주의] 해당 controller는 아직 완성되지 않았습니다. 추후 message쪽 controller를 완성 후 lastMessage와 unreadCount를 붙이는 작업이
     * 추가로 필요합니다.
     */
    @GetMapping
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public ResponseEntity<ApiResponse<ChatRoomListResponse>> getMyRooms(
            @AuthenticationPrincipal Long memberId,
            @Valid @ModelAttribute ChatRoomGetRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(chatRoomService.getMyRooms(request, memberId)));
    }

    /**
     * 5-3. 게임별 채팅방 조회
     */
    @GetMapping("/game/{gameId}")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public ResponseEntity<ApiResponse<ChatRoomListResponse>> getRoomsByGameId(
            @Valid @ModelAttribute ChatRoomGetByGameRequest request,
            @PathVariable Long gameId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(chatRoomService.getRoomsByGameId(request, gameId)));
    }

    @PatchMapping("/{roomId}")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public ResponseEntity<ApiResponse<ChatRoomUpdateResponse>> update(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long roomId,
            @Valid @RequestBody ChatRoomUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(chatRoomService.update(request, roomId, memberId)));
    }

    @DeleteMapping("/{roomId}")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long roomId
    ) {
        chatRoomService.delete(roomId, memberId);
        return ResponseEntity.noContent().build();
    }


    @GetMapping("/{roomId}")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public ResponseEntity<ApiResponse<ChatRoomDetailResponse>> getRoomDetail(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long roomId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(chatRoomService.getRoomDetail(roomId, memberId)));
    }
}
