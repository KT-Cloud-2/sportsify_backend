package com.sportsify.ticketing.presentation;

import com.sportsify.common.exception.BusinessException;
import com.sportsify.common.exception.ErrorCode;
import com.sportsify.support.WebMvcTestSupport;
import com.sportsify.ticketing.application.service.ReservationService;
import com.sportsify.ticketing.domain.model.OrderStatus;
import com.sportsify.ticketing.presentation.controller.ReservationController;
import com.sportsify.ticketing.presentation.dto.ReservationSeatsRequestDto;
import com.sportsify.ticketing.presentation.dto.ReservationSeatsResponseDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReservationController.class)
class ReservationControllerApiTest extends WebMvcTestSupport {

    private static final Long TEST_MEMBER_ID = 1L;
    private static final ReservationSeatsRequestDto reqDto = ReservationSeatsRequestDto.from(1L, List.of(1L));

    @MockitoBean
    private ReservationService reservationService;


    // ──────────────────────── GET /api/seats/reservations ────────────────────────
    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("POST /api/seats/reservations — 200 좌석 선점 성공")
    void success() throws Exception {

        ReservationSeatsResponseDto resDto = new ReservationSeatsResponseDto(
                1L,
                1L,
                1L,
                OrderStatus.PENDING,
                LocalDateTime.now(),
                List.of(mock(ReservationSeatsResponseDto.ReservationSeatDto.class)),
                LocalDateTime.now()
        );

        when(reservationService.reserveSeat(any(), any())).thenReturn(resDto);

        mockMvc.perform(post("/api/seats/reservations")
                        .header("Authorization", bearerToken(TEST_MEMBER_ID, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(1L))
                .andExpect(jsonPath("$.memberId").value(1L))
                .andExpect(jsonPath("$.gameId").value(1L))
                .andExpect(jsonPath("$.status").value("PENDING"));


    }

    @Test
    @DisplayName("POST /api/seats/reservations — 404 존재하지 않는 경기")
    void gameNotFound() throws Exception {

        when(reservationService.reserveSeat(any(), any())).thenThrow(new BusinessException(ErrorCode.GAME_NOT_FOUND));

        mockMvc.perform(post("/api/seats/reservations")
                        .header("Authorization", bearerToken(TEST_MEMBER_ID, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqDto)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GAME_NOT_FOUND"));
    }


    @Test
    @DisplayName("POST /api/seats/reservations — 404 존재하지 않는 좌석")
    void seatNotFound() throws Exception {

        when(reservationService.reserveSeat(any(), any())).thenThrow(new BusinessException(ErrorCode.SEAT_NOT_FOUND));

        mockMvc.perform(post("/api/seats/reservations")
                        .header("Authorization", bearerToken(TEST_MEMBER_ID, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqDto)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SEAT_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /api/seats/reservations — 409 이미 선점된 좌석")
    void seatAlreadyReserved() throws Exception {

        when(reservationService.reserveSeat(any(), any())).thenThrow(new BusinessException(ErrorCode.SEAT_ALREADY_RESERVED));

        mockMvc.perform(post("/api/seats/reservations")
                        .header("Authorization", bearerToken(TEST_MEMBER_ID, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqDto)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_ALREADY_RESERVED"));
    }


    @Test
    @DisplayName("POST /api/seats/reservations — 422 판매 중 아닌 경기")
    void gameNotOnSale() throws Exception {

        when(reservationService.reserveSeat(any(), any())).thenThrow(new BusinessException(ErrorCode.GAME_NOT_ON_SALE));

        mockMvc.perform(post("/api/seats/reservations")
                        .header("Authorization", bearerToken(TEST_MEMBER_ID, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqDto)))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("GAME_NOT_ON_SALE"));
    }


    @Test
    @DisplayName("POST /api/seats/reservations — 422 최대 좌석 선점 개수 초과")
    void exceedTicketLimit() throws Exception {

        when(reservationService.reserveSeat(any(), any())).thenThrow(new BusinessException(ErrorCode.TICKET_LIMIT_EXCEEDED));

        mockMvc.perform(post("/api/seats/reservations")
                        .header("Authorization", bearerToken(TEST_MEMBER_ID, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqDto)))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("TICKET_LIMIT_EXCEEDED"));
    }

}
