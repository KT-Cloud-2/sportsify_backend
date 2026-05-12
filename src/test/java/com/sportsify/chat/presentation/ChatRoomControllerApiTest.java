package com.sportsify.chat.presentation;

import com.sportsify.chat.application.chatRoom.dto.*;
import com.sportsify.chat.application.chatRoom.service.ChatRoomService;
import com.sportsify.chat.presentation.chatRoom.controller.ChatRoomController;
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
        ChatRoomSummaryResponse item = new ChatRoomSummaryResponse(ROOM_ID, "GAME", GAME_ID, "한화 VS LG", null, 3L, null, true, NOW, NOW);
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
}
