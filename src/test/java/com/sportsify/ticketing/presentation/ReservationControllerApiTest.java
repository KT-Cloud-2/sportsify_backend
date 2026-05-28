package com.sportsify.ticketing.presentation;

import com.sportsify.support.WebMvcTestSupport;
import com.sportsify.ticketing.application.service.ReservationService;
import com.sportsify.ticketing.presentation.controller.ReservationController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReservationController.class)
class ReservationControllerApiTest extends WebMvcTestSupport {

    @MockitoBean
    private ReservationService reservationService;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("POST /api/seats/reservations — 400 gameId 누락")
    void missingGameId() throws Exception {
        mockMvc.perform(post("/api/seats/reservations")
                        .header("Authorization", bearerToken(1L, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"seatIds": [1]}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/seats/reservations — 400 seatIds 빈 배열")
    void emptySeatIds() throws Exception {
        mockMvc.perform(post("/api/seats/reservations")
                        .header("Authorization", bearerToken(1L, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gameId": 1, "seatIds": []}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/seats/reservations — 401 토큰 없음")
    void unauthorized() throws Exception {
        mockMvc.perform(post("/api/seats/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gameId": 1, "seatIds": [1]}
                                """))
                .andExpect(status().isUnauthorized());
    }
}
