package com.sportsify.chat.presentation.chatRoom.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sportsify.chat.application.chatRoom.dto.*;
import com.sportsify.chat.application.chatRoom.service.ChatRoomService;
import com.sportsify.chat.infrastructure.api.ChatRoomApi;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat/rooms")
@RequiredArgsConstructor
public class ChatRoomController implements ChatRoomApi {

    private final ChatRoomService chatRoomService;

    /**
     * 5-1. 채팅방 생성
     *
     * @param memberId
     * @param request  CreateChatRoomRequest
     * @return ResponseEntity<CommonResponse < ChatRoomResponse>>
     */
    @PostMapping
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public ResponseEntity<ChatRoomResponse> create(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody CreateChatRoomRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(chatRoomService.create(request, memberId));
    }

    /**
     * 5-2. 내 채팅방 목록 조회
     * [!주의] 해당 controller는 아직 완성되지 않았습니다. 추후 message쪽 controller를 완성 후 lastMessage와 unreadCount를 붙이는 작업이
     * 추가로 필요합니다.
     *
     * @param memberId
     * @param request  ChatRoomListResponse
     * @return ResponseEntity<CommonResponse < ChatRoomListResponse>>
     */
    @GetMapping
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public ResponseEntity<ChatRoomListResponse> getMyRooms(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody ChatRoomGetRequest request
    ) {
        return ResponseEntity.ok(chatRoomService.getMyRooms(request, memberId));
    }

    /**
     * 5-3. 게임별 채팅방 조회
     *
     * @param request CommonResponse<ChatRoomListResponse>
     * @param gameId
     * @return ResponseEntity<CommonResponse < ChatRoomListResponse>>
     */
    @GetMapping("/game/{gameId}")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public ResponseEntity<ChatRoomListResponse> getRoomsByGameId(
            @Valid @ModelAttribute ChatRoomGetByGameRequest request,
            @PathVariable Long gameId
    ) {
        return ResponseEntity.ok(chatRoomService.getRoomsByGameId(request, gameId));
    }

    /**
     * 5-4. 채팅방 상세 조회
     *
     * @param memberId
     * @param roomId
     * @return ResponseEntity<CommonResponse < ChatRoomDetailResponse>>
     */
    @GetMapping("/{roomId}")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public ResponseEntity<ChatRoomDetailResponse> getRoomDetail(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long roomId
    ) {
        return ResponseEntity.ok(chatRoomService.getRoomDetail(roomId, memberId));
    }

    /**
     * 5-5. 채팅방 정보 수정
     *
     * @param memberId
     * @param roomId
     * @param request  ChatRoomUpdateRequest
     * @return ResponseEntity<CommonResponse < ChatRoomUpdateResponse>>
     */
    @PatchMapping("/{roomId}")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public ResponseEntity<ChatRoomUpdateResponse> update(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long roomId,
            @Valid @RequestBody ChatRoomUpdateRequest request
    ) {
        return ResponseEntity.ok(chatRoomService.update(request, roomId, memberId));
    }

    /**
     * 5-6. 채팅방 삭제
     *
     * @param memberId
     * @param roomId
     * @return ResponseEntity<Void>
     */
    @DeleteMapping("/{roomId}")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long roomId
    ) {
        chatRoomService.delete(roomId, memberId);
        return ResponseEntity.noContent().build();
    }


}
