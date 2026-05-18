package com.sportsify.chat.presentation;

import com.sportsify.chat.application.message.dto.*;
import com.sportsify.chat.application.message.service.MessageService;
import com.sportsify.chat.presentation.message.controller.MessageController;
import com.sportsify.support.WebMvcTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.sportsify.common.exception.BusinessException;
import com.sportsify.common.exception.ErrorCode;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MessageController.class)
class MessageControllerApiTest extends WebMvcTestSupport {

    private static final Long MEMBER_ID = 1L;
    private static final Long ROOM_ID = 10L;
    private static final Long MESSAGE_ID = 100L;
    private static final Instant NOW_INSTANT = Instant.parse("2026-05-04T12:00:00Z");
    private static final LocalDateTime NOW = LocalDateTime.ofInstant(NOW_INSTANT, ZoneOffset.UTC);

    @MockitoBean
    private MessageService messageService;

    // ──────────────────────── GET /api/chat/messages/history/{roomId} ────────────────────────

    @Test
    @DisplayName("GET /api/chat/messages/history/{roomId} — 200 채팅 이력 조회 성공")
    void 채팅_이력_조회_성공() throws Exception {
        MessageSummaryResponse item = new MessageSummaryResponse(MESSAGE_ID, ROOM_ID, "TEXT", "ACTIVE", NOW_INSTANT);
        MessageListResponse response = new MessageListResponse(List.of(item), null, null, false, 1);

        given(messageService.getHistory(any(MessagePageNationRequest.class), eq(ROOM_ID), eq(MEMBER_ID)))
                .willReturn(response);

        mockMvc.perform(get("/api/chat/messages/history/{roomId}", ROOM_ID)
                        .param("limit", "20")
                        .header("Authorization", bearerToken(MEMBER_ID, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(1))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.totalCount").value(1));
    }

    // ──────────────────────── DELETE /api/chat/messages/{messageId} ────────────────────────

    @Test
    @DisplayName("DELETE /api/chat/messages/{messageId} — 200 메시지 삭제 성공")
    void 메시지_삭제_성공() throws Exception {
        MessageDeleteResponse response = new MessageDeleteResponse(MESSAGE_ID, ROOM_ID, "DELETED");

        given(messageService.delete(MESSAGE_ID, MEMBER_ID)).willReturn(response);

        mockMvc.perform(delete("/api/chat/messages/{messageId}", MESSAGE_ID)
                        .header("Authorization", bearerToken(MEMBER_ID, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messageId").value(MESSAGE_ID))
                .andExpect(jsonPath("$.roomId").value(ROOM_ID))
                .andExpect(jsonPath("$.status").value("DELETED"));
    }

    // ──────────────────────── GET /api/chat/messages/getMessages/{roomId} ────────────────────────

    @Test
    @DisplayName("GET /api/chat/messages/getMessages/{roomId} — 200 채팅방 메시지 조회 성공")
    void 채팅방_메시지_조회_성공() throws Exception {
        MessageResponse item = new MessageResponse(MESSAGE_ID, MEMBER_ID, "TEXT", "ACTIVE", "안녕하세요", NOW_INSTANT);
        MessageListResponse response = new MessageListResponse(List.of(item), List.of(), MESSAGE_ID, true, 1);

        given(messageService.getMessages(any(MessagePageNationRequest.class), eq(ROOM_ID), eq(MEMBER_ID)))
                .willReturn(response);

        mockMvc.perform(get("/api/chat/messages/getMessages/{roomId}", ROOM_ID)
                        .header("Authorization", bearerToken(MEMBER_ID, "USER"))
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(1))
                .andExpect(jsonPath("$.nextCursor").value(MESSAGE_ID))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.totalCount").value(1));
    }

    // ──────────────────────── API 실패 Test ────────────────────────

    /**
     * 인증 없이 삭제 요청 시 401이 반환되어야 한다.
     * 메시지 삭제는 작성자만 가능하므로 인증 필터가 필수 관문이다.
     */
    @Test
    @DisplayName("DELETE /api/chat/messages/{messageId} — 401 인증 없이 메시지 삭제 시 실패")
    void 메시지_삭제_인증없음_401() throws Exception {
        mockMvc.perform(delete("/api/chat/messages/{messageId}", MESSAGE_ID))
                .andExpect(status().isUnauthorized());
    }

    /**
     * 존재하지 않는 메시지 삭제 시 서비스가 NOT_FOUND를 던지고 404로 응답해야 한다.
     */
    @Test
    @DisplayName("DELETE /api/chat/messages/{messageId} — 404 존재하지 않는 메시지 삭제 시 실패")
    void 메시지_삭제_없는메시지_404() throws Exception {
        given(messageService.delete(MESSAGE_ID, MEMBER_ID))
                .willThrow(new BusinessException(ErrorCode.NOT_FOUND, "메시지가 없습니다."));

        mockMvc.perform(delete("/api/chat/messages/{messageId}", MESSAGE_ID)
                        .header("Authorization", bearerToken(MEMBER_ID, "USER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    /**
     * 다른 사람의 메시지를 삭제하면 서비스가 FORBIDDEN을 던지고 403으로 응답해야 한다.
     * 실패 포인트: 소유권 검증이 누락되면 타인 메시지 삭제가 허용됨.
     */
    @Test
    @DisplayName("DELETE /api/chat/messages/{messageId} — 403 다른 사람 메시지 삭제 시 실패")
    void 메시지_삭제_권한없음_403() throws Exception {
        given(messageService.delete(MESSAGE_ID, MEMBER_ID))
                .willThrow(new BusinessException(ErrorCode.FORBIDDEN, "삭제 권한이 없습니다."));

        mockMvc.perform(delete("/api/chat/messages/{messageId}", MESSAGE_ID)
                        .header("Authorization", bearerToken(MEMBER_ID, "USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
