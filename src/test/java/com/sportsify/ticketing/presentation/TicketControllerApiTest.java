package com.sportsify.ticketing.presentation;

import com.sportsify.support.WebMvcTestSupport;
import com.sportsify.ticketing.application.dto.TicketItemDto;
import com.sportsify.ticketing.application.service.TicketService;
import com.sportsify.ticketing.presentation.controller.TicketController;
import com.sportsify.ticketing.presentation.dto.TicketListResponseDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TicketController.class)
class TicketControllerApiTest extends WebMvcTestSupport {

    private static final Long TEST_MEMBER_ID = 1L;

    @MockitoBean
    private TicketService ticketService;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("GET /api/tickets — 200 티켓 목록 조회 성공")
    void getMyTickets_success() throws Exception {
        TicketItemDto item = TicketItemDto.builder()
                .ticketId(1L)
                .ticketNumber("uuid-1")
                .gameId(1L)
                .sportType("BASEBALL")
                .team1Name("두산")
                .team2Name("LG")
                .gameTime(LocalDateTime.of(2025, 5, 1, 18, 30))
                .venue("잠실")
                .seatGrade("VIP")
                .seatSection("A구역")
                .seatNumber("12")
                .price(15000)
                .status("CONFIRMED")
                .issuedAt(LocalDateTime.of(2025, 4, 25, 10, 0))
                .build();

        TicketListResponseDto response = new TicketListResponseDto(
                List.of(item), 0, 1, 1L, false
        );

        when(ticketService.getMyTickets(eq(TEST_MEMBER_ID), anyInt(), anyInt()))
                .thenReturn(response);

        mockMvc.perform(get("/api/tickets")
                        .header("Authorization", bearerToken(TEST_MEMBER_ID, "USER"))
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].ticketId").value(1L))
                .andExpect(jsonPath("$.items[0].ticketNumber").value("uuid-1"))
                .andExpect(jsonPath("$.items[0].sportType").value("BASEBALL"))
                .andExpect(jsonPath("$.items[0].team1Name").value("두산"))
                .andExpect(jsonPath("$.items[0].team2Name").value("LG"))
                .andExpect(jsonPath("$.items[0].venue").value("잠실"))
                .andExpect(jsonPath("$.items[0].seatGrade").value("VIP"))
                .andExpect(jsonPath("$.items[0].price").value(15000))
                .andExpect(jsonPath("$.items[0].status").value("CONFIRMED"))
                .andExpect(jsonPath("$.currentPage").value(0))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    @DisplayName("GET /api/tickets — 200 티켓 없을 때 빈 리스트")
    void getMyTickets_empty() throws Exception {
        TicketListResponseDto response = new TicketListResponseDto(
                List.of(), 0, 0, 0L, false
        );

        when(ticketService.getMyTickets(eq(TEST_MEMBER_ID), anyInt(), anyInt()))
                .thenReturn(response);

        mockMvc.perform(get("/api/tickets")
                        .header("Authorization", bearerToken(TEST_MEMBER_ID, "USER"))
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalCount").value(0));
    }

    @Test
    @DisplayName("GET /api/tickets — 401 인증 없이 접근 시 실패")
    void getMyTickets_unauthorized() throws Exception {
        mockMvc.perform(get("/api/tickets")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/tickets — 400 잘못된 페이지 파라미터")
    void getMyTickets_invalidPage() throws Exception {
        mockMvc.perform(get("/api/tickets")
                        .header("Authorization", bearerToken(TEST_MEMBER_ID, "USER"))
                        .param("page", "-1")
                        .param("size", "10"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/tickets — 400 잘못된 사이즈 파라미터")
    void getMyTickets_invalidSize() throws Exception {
        mockMvc.perform(get("/api/tickets")
                        .header("Authorization", bearerToken(TEST_MEMBER_ID, "USER"))
                        .param("page", "0")
                        .param("size", "0"))
                .andExpect(status().isBadRequest());
    }
}
