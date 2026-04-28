package com.sportsify.chat.presentation.chatRoom;

import com.sportsify.chat.application.chatRoom.ChatRoomUpdateResponse;
import com.sportsify.chat.application.chatRoom.UpdateChatRoomRequest;
import com.sportsify.chat.application.chatRoom.dto.ChatRoomCreateResponseDto;
import com.sportsify.chat.application.chatRoom.dto.CreateChatRoomRequestDto;
import com.sportsify.chat.application.chatRoom.service.ChatRoomService;
import com.sportsify.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatRoomController {

    private final ChatRoomService chatRoomService;

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<ChatRoomCreateResponseDto>> create(
            @RequestBody @Valid CreateChatRoomRequestDto request,
            @RequestHeader("X-Member-Id") Long memberId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(chatRoomService.create(request, memberId)));
    }

    @GetMapping("/rooms")
    public ResponseEntity<ApiResponse<ChatRoomListResponse>> getMyRooms(
            @RequestParam String type,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int limit,
            @RequestHeader("X-Member-Id") Long memberId) {
        return ResponseEntity.ok(ApiResponse.ok(
                chatRoomService.getMyRooms(memberId, type, cursor, limit)));
    }

    @GetMapping("/rooms/game/{gameId}")
    public ResponseEntity<ApiResponse<ChatRoomListResponse>> getRoomsByGame(
            @PathVariable Long gameId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(ApiResponse.ok(
                chatRoomService.getRoomsByGame(gameId, cursor, limit)));
    }

    @GetMapping("/rooms/{roomId}")
    public ResponseEntity<ApiResponse<ChatRoomDetailResponse>> getDetail(
            @PathVariable Long roomId,
            @RequestHeader(value = "X-Member-Id", required = false) Long memberId) {
        return ResponseEntity.ok(ApiResponse.ok(
                chatRoomService.getDetail(roomId, memberId)));
    }

    @PatchMapping("/rooms/{roomId}")
    public ResponseEntity<ApiResponse<ChatRoomUpdateResponse>> update(
            @PathVariable Long roomId,
            @RequestBody @Valid UpdateChatRoomRequest request,
            @RequestHeader("X-Member-Id") Long memberId) {
        return ResponseEntity.ok(ApiResponse.ok(
                chatRoomService.update(roomId, request, memberId)));
    }
}
