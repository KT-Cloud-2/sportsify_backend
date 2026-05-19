package com.sportsify.chat.presentation;

import com.sportsify.chat.application.chatRoom.dto.*;
import com.sportsify.chat.application.chatRoom.service.ChatRoomService;
import com.sportsify.chat.presentation.chatRoom.controller.ChatRoomController;
import com.sportsify.common.exception.BusinessException;
import com.sportsify.common.exception.ErrorCode;
import com.sportsify.support.WebMvcTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatRoomController.class)
class ChatRoomControllerApiTest extends WebMvcTestSupport {

    private static final Long MEMBER_ID = 1L;
    private static final Long ROOM_ID = 10L;
    private static final Long GAME_ID = 5L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 4, 12, 0);
    @MockitoBean
    private ChatRoomService chatRoomService;

    // ──────────────────────── API 성공 Test ────────────────────────

    @Test
    @DisplayName("POST /api/chat/rooms — 201 채팅방 생성 성공")
    void 채팅방_생성_성공() throws Exception {
        ChatRoomResponse response = new ChatRoomResponse(ROOM_ID, "GAME", GAME_ID, "한화 VS LG", "https://img.png", MEMBER_ID, NOW);
        given(chatRoomService.create(any(CreateChatRoomRequest.class), eq(MEMBER_ID))).willReturn(response);

        mockMvc.perform(post("/api/chat/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateChatRoomRequest("GAME", "한화 VS LG", "https://img.png", GAME_ID, List.of(2L))))
                        .header("Authorization", bearerToken(MEMBER_ID, "USER")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roomId").value(ROOM_ID))
                .andExpect(jsonPath("$.type").value("GAME"))
                .andExpect(jsonPath("$.name").value("한화 VS LG"));
    }

    @Test
    @DisplayName("GET /api/chat/rooms — 200 내 채팅방 목록 조회 성공")
    void 내채팅방_목록_조회_성공() throws Exception {
        ChatRoomSummaryResponse item = new ChatRoomSummaryResponse(ROOM_ID, "GAME", GAME_ID, "한화 VS LG", null, 3L, null, 0L, true, NOW, NOW);
        ChatRoomListResponse response = new ChatRoomListResponse(List.of(item), null, false, 1);
        given(chatRoomService.getMyRooms(any(ChatRoomGetRequest.class), eq(MEMBER_ID))).willReturn(response);

        mockMvc.perform(get("/api/chat/rooms")
                        .param("type", "GAME")
                        .param("limit", "20")
                        .header("Authorization", bearerToken(MEMBER_ID, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.items[0].roomId").value(ROOM_ID));
    }

    @Test
    @DisplayName("GET /api/chat/rooms/game/{gameId} — 200 게임별 채팅방 조회 성공")
    void 게임별_채팅방_조회_성공() throws Exception {
        ChatRoomGetByGameResponse item = new ChatRoomGetByGameResponse(ROOM_ID, "GAME", GAME_ID, "한화 VS LG", null, 3L, NOW);
        ChatRoomListResponse response = new ChatRoomListResponse(List.of(item), null, false, 1);
        given(chatRoomService.getRoomsByGameId(any(ChatRoomGetByGameRequest.class), eq(GAME_ID))).willReturn(response);

        mockMvc.perform(get("/api/chat/rooms/game/{gameId}", GAME_ID)
                        .header("Authorization", bearerToken(MEMBER_ID, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.items[0].roomId").value(ROOM_ID));
    }

    @Test
    @DisplayName("GET /api/chat/rooms/{roomId} — 200 채팅방 상세 조회 성공")
    void 채팅방_상세_조회_성공() throws Exception {
        ChatRoomDetailResponse response = new ChatRoomDetailResponse(ROOM_ID, "GAME", GAME_ID, "한화 VS LG", null, 5L, MEMBER_ID, NOW, Optional.empty());
        given(chatRoomService.getRoomDetail(eq(ROOM_ID), eq(MEMBER_ID))).willReturn(response);

        mockMvc.perform(get("/api/chat/rooms/{roomId}", ROOM_ID)
                        .header("Authorization", bearerToken(MEMBER_ID, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").value(ROOM_ID))
                .andExpect(jsonPath("$.currentParticipants").value(5));
    }

    @Test
    @DisplayName("PATCH /api/chat/rooms/{roomId} — 200 채팅방 정보 수정 성공")
    void 채팅방_정보_수정_성공() throws Exception {
        ChatRoomUpdateResponse response = new ChatRoomUpdateResponse(ROOM_ID, "새로운 채팅방", "https://new.png", NOW);
        given(chatRoomService.update(any(ChatRoomUpdateRequest.class), eq(ROOM_ID), eq(MEMBER_ID))).willReturn(response);

        mockMvc.perform(patch("/api/chat/rooms/{roomId}", ROOM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChatRoomUpdateRequest("새로운 채팅방", "https://new.png")))
                        .header("Authorization", bearerToken(MEMBER_ID, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").value(ROOM_ID))
                .andExpect(jsonPath("$.name").value("새로운 채팅방"));
    }

    @Test
    @DisplayName("DELETE /api/chat/rooms/{roomId} — 204 채팅방 삭제 성공")
    void 채팅방_삭제_성공() throws Exception {
        doNothing().when(chatRoomService).delete(eq(ROOM_ID), eq(MEMBER_ID));

        mockMvc.perform(delete("/api/chat/rooms/{roomId}", ROOM_ID)
                        .header("Authorization", bearerToken(MEMBER_ID, "USER")))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("PATCH /api/chat/rooms/{roomId}/archive - 200 채팅방 아카이브 성공")
    void archive_success() throws Exception {
        ChatRoomArchiveResponse response =
                new ChatRoomArchiveResponse(ROOM_ID, "ARCHIVED", NOW);

        given(chatRoomService.archive(ROOM_ID, MEMBER_ID))
                .willReturn(response);

        mockMvc.perform(patch("/api/chat/rooms/{roomId}/archive", ROOM_ID)
                        .header("Authorization", bearerToken(MEMBER_ID, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").value(ROOM_ID))
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
    }

    @Test
    @DisplayName("PATCH /api/chat/rooms/{roomId}/unarchive - 200 채팅방 아카이브 취소 성공")
    void unarchive_success() throws Exception {
        ChatRoomArchiveResponse response =
                new ChatRoomArchiveResponse(ROOM_ID, "UNARCHIVED", NOW);

        given(chatRoomService.unarchive(ROOM_ID, MEMBER_ID))
                .willReturn(response);

        mockMvc.perform(patch("/api/chat/rooms/{roomId}/unarchive", ROOM_ID)
                        .header("Authorization", bearerToken(MEMBER_ID, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").value(ROOM_ID))
                .andExpect(jsonPath("$.status").value("UNARCHIVED"));
    }

    // ──────────────────────── API 실패 Test ────────────────────────

    /**
     * 서비스가 FORBIDDEN을 던지면 GlobalExceptionHandler가 403으로 변환해야 한다.
     * 방장이 아닌 멤버가 삭제를 시도하는 경우.
     * 실패 포인트: 권한 에러가 500으로 뭉개지면 클라이언트가 재시도 여부를 판단할 수 없음.
     */
    @Test
    @DisplayName("DELETE /api/chat/rooms/{roomId} — 403 방장이 아닌 멤버가 삭제 시도하면 실패")
    void 채팅방_삭제_권한없음_403() throws Exception {
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.FORBIDDEN, "방장만 삭제할 수 있습니다."))
                .when(chatRoomService).delete(eq(ROOM_ID), eq(MEMBER_ID));

        mockMvc.perform(delete("/api/chat/rooms/{roomId}", ROOM_ID)
                        .header("Authorization", bearerToken(MEMBER_ID, "USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    /**
     * @NotBlank 검증 실패 시 GlobalExceptionHandler가 400과 INVALID_INPUT 코드를 반환해야 한다.
     * 실패 포인트: @Valid 누락 시 잘못된 type으로도 서비스가 호출됨.
     */
    @Test
    @DisplayName("POST /api/chat/rooms — 400 type 필드가 빈 값이면 유효성 검사 실패")
    void 채팅방_생성_type빈값_400() throws Exception {
        mockMvc.perform(post("/api/chat/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateChatRoomRequest("", "한화 VS LG", null, GAME_ID, List.of())))
                        .header("Authorization", bearerToken(MEMBER_ID, "USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    /**
     * 서비스가 NOT_FOUND를 던지면 GlobalExceptionHandler가 404로 변환해야 한다.
     * 실패 포인트: @RestControllerAdvice가 누락되면 500이 반환됨.
     */
    @Test
    @DisplayName("GET /api/chat/rooms/{roomId} — 404 존재하지 않는 채팅방 조회 시 실패")
    void 채팅방_조회_없는방_404() throws Exception {
        given(chatRoomService.getRoomDetail(eq(ROOM_ID), eq(MEMBER_ID)))
                .willThrow(new BusinessException(ErrorCode.NOT_FOUND, "채팅방이 없습니다."));

        mockMvc.perform(get("/api/chat/rooms/{roomId}", ROOM_ID)
                        .header("Authorization", bearerToken(MEMBER_ID, "USER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    /**
     * 방장이 아닌 멤버가 아카이브 요청 시 서비스가 FORBIDDEN을 던지면 403으로 변환되어야 한다.
     * 실패 포인트: GlobalExceptionHandler가 FORBIDDEN을 처리하지 않으면 500이 반환됨.
     */
    @Test
    @DisplayName("PATCH /api/chat/rooms/{roomId}/archive — 403 방장이 아닌 멤버가 아카이브 시도하면 실패")
    void 채팅방_아카이브_권한없음_403() throws Exception {
        given(chatRoomService.archive(eq(ROOM_ID), eq(MEMBER_ID)))
                .willThrow(new BusinessException(ErrorCode.FORBIDDEN, "Only room leader can archive room"));

        mockMvc.perform(patch("/api/chat/rooms/{roomId}/archive", ROOM_ID)
                        .header("Authorization", bearerToken(MEMBER_ID, "USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    /**
     * 방장이 아닌 멤버가 언아카이브 요청 시 서비스가 FORBIDDEN을 던지면 403으로 변환되어야 한다.
     * 실패 포인트: GlobalExceptionHandler가 FORBIDDEN을 처리하지 않으면 500이 반환됨.
     */
    @Test
    @DisplayName("PATCH /api/chat/rooms/{roomId}/unarchive — 403 방장이 아닌 멤버가 언아카이브 시도하면 실패")
    void 채팅방_언아카이브_권한없음_403() throws Exception {
        given(chatRoomService.unarchive(eq(ROOM_ID), eq(MEMBER_ID)))
                .willThrow(new BusinessException(ErrorCode.FORBIDDEN, "Only room leader can unarchive room"));

        mockMvc.perform(patch("/api/chat/rooms/{roomId}/unarchive", ROOM_ID)
                        .header("Authorization", bearerToken(MEMBER_ID, "USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
