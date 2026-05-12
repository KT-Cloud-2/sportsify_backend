package com.sportsify.chat.presentation.message.controller;

import com.sportsify.chat.application.message.dto.MessageDeleteResponse;
import com.sportsify.chat.application.message.dto.MessageListResponse;
import com.sportsify.chat.application.message.dto.MessagePageNationRequest;
import com.sportsify.chat.application.message.service.MessageService;
import com.sportsify.chat.infrastructure.api.MessageApi;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat/messages")
@RequiredArgsConstructor
public class MessageController implements MessageApi {
    private final MessageService messageService;

    /**
     * 5-12. 채팅 이력 조회
     *
     * @param memberId
     * @param roomId
     * @param request  MessagePageNationRequest
     * @return ResponseEntity<MessageListResponse>
     */
    @GetMapping("/history/{roomId}")
    public ResponseEntity<MessageListResponse> history(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long roomId,
            @Valid @ModelAttribute MessagePageNationRequest request
    ) {
        return ResponseEntity.ok(messageService.getHistory(request, roomId, memberId));
    }

    /**
     * 5-14. 메시지 삭제
     *
     * @param memberId
     * @param messageId
     * @return ResponseEntity<MessageDeleteResponse>
     */
    @DeleteMapping("/{messageId}")
    public ResponseEntity<MessageDeleteResponse> delete(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long messageId) {
        return ResponseEntity.ok(messageService.delete(messageId, memberId));
    }

    /**
     * 5-15. 채팅방 메시지 조회
     *
     * @param memberId
     * @param roomId
     * @param request  MessagePageNationRequest
     * @return ResponseEntity<CommonResponse < MessageListResponse>>
     */
    @GetMapping("getMessages/{roomId}")
    public ResponseEntity<MessageListResponse> getMessages(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long roomId,
            @Valid @ModelAttribute MessagePageNationRequest request
    ) {
        return ResponseEntity.ok(messageService.getMessages(request, roomId, memberId));
    }

}
