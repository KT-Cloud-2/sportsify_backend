package com.sportsify.game.presentation;

import com.sportsify.common.exception.BusinessException;
import com.sportsify.common.exception.ErrorCode;
import com.sportsify.game.application.service.GameService;
import com.sportsify.game.domain.model.DayType;
import com.sportsify.game.domain.model.GameGrade;
import com.sportsify.game.domain.model.GameStatus;
import com.sportsify.game.presentation.controller.GameController;
import com.sportsify.game.presentation.dto.GameCreateRequestDto;
import com.sportsify.game.presentation.dto.GameCreateResponseDto;
import com.sportsify.support.WebMvcTestSupport;
import com.sportsify.team.domain.model.SportType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GameController.class)
class GameControllerApiTest extends WebMvcTestSupport {

    private static final Long TEST_MEMBER_ID = 1L;

    @MockitoBean
    private GameService gameService;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("POST /api/games — 201 경기 생성 성공")
    void createGame_success() throws Exception {
        LocalDateTime startAt = LocalDateTime.of(2025, 5, 1, 18, 30);
        LocalDateTime saleStartAt = LocalDateTime.of(2025, 4, 25, 10, 0);
        LocalDateTime saleEndAt = LocalDateTime.of(2025, 5, 1, 18, 0);
        LocalDateTime createdAt = LocalDateTime.of(2025, 4, 1, 12, 0);

        GameCreateRequestDto request = new GameCreateRequestDto(
                1L, 1L, 2L, SportType.BASEBALL,
                startAt, 180, GameStatus.SCHEDULED,
                DayType.WEEKDAY, GameGrade.NORMAL, 4,
                saleStartAt, saleEndAt
        );

        GameCreateResponseDto response = new GameCreateResponseDto(
                1L, 1L, 1L, 2L, SportType.BASEBALL,
                startAt, 180, GameStatus.SCHEDULED,
                DayType.WEEKDAY, GameGrade.NORMAL, 4,
                saleStartAt, saleEndAt, createdAt
        );

        when(gameService.createGame(any())).thenReturn(response);

        mockMvc.perform(post("/api/games")
                        .header("Authorization", bearerToken(TEST_MEMBER_ID, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.stadiumId").value(1L))
                .andExpect(jsonPath("$.homeTeamId").value(1L))
                .andExpect(jsonPath("$.awayTeamId").value(2L))
                .andExpect(jsonPath("$.sportType").value("BASEBALL"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.durationMinutes").value(180))
                .andExpect(jsonPath("$.maxTicketPerUser").value(4));
    }

    @Test
    @DisplayName("POST /api/games — 400 stadiumId 누락 시 실패")
    void createGame_missingStadiumId() throws Exception {
        GameCreateRequestDto request = new GameCreateRequestDto(
                null, 1L, 2L, SportType.BASEBALL,
                LocalDateTime.now().plusDays(7), 180, GameStatus.SCHEDULED,
                DayType.WEEKDAY, GameGrade.NORMAL, 4, null, null
        );

        mockMvc.perform(post("/api/games")
                        .header("Authorization", bearerToken(TEST_MEMBER_ID, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/games — 400 startAt 누락 시 실패")
    void createGame_missingStartAt() throws Exception {
        GameCreateRequestDto request = new GameCreateRequestDto(
                1L, 1L, 2L, SportType.BASEBALL,
                null, 180, GameStatus.SCHEDULED,
                DayType.WEEKDAY, GameGrade.NORMAL, 4, null, null
        );

        mockMvc.perform(post("/api/games")
                        .header("Authorization", bearerToken(TEST_MEMBER_ID, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/games — 400 status 누락 시 실패")
    void createGame_missingStatus() throws Exception {
        GameCreateRequestDto request = new GameCreateRequestDto(
                1L, 1L, 2L, SportType.BASEBALL,
                LocalDateTime.now().plusDays(7), 180, null,
                DayType.WEEKDAY, GameGrade.NORMAL, 4, null, null
        );

        mockMvc.perform(post("/api/games")
                        .header("Authorization", bearerToken(TEST_MEMBER_ID, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/games — 404 존재하지 않는 경기장")
    void createGame_stadiumNotFound() throws Exception {
        GameCreateRequestDto request = new GameCreateRequestDto(
                999L, 1L, 2L, SportType.BASEBALL,
                LocalDateTime.now().plusDays(7), 180, GameStatus.SCHEDULED,
                DayType.WEEKDAY, GameGrade.NORMAL, 4, null, null
        );

        when(gameService.createGame(any()))
                .thenThrow(new BusinessException(ErrorCode.STADIUM_NOT_FOUND));

        mockMvc.perform(post("/api/games")
                        .header("Authorization", bearerToken(TEST_MEMBER_ID, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STADIUM_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /api/games — 404 존재하지 않는 팀")
    void createGame_teamNotFound() throws Exception {
        GameCreateRequestDto request = new GameCreateRequestDto(
                1L, 999L, 2L, SportType.BASEBALL,
                LocalDateTime.now().plusDays(7), 180, GameStatus.SCHEDULED,
                DayType.WEEKDAY, GameGrade.NORMAL, 4, null, null
        );

        when(gameService.createGame(any()))
                .thenThrow(new BusinessException(ErrorCode.TEAM_NOT_FOUND));

        mockMvc.perform(post("/api/games")
                        .header("Authorization", bearerToken(TEST_MEMBER_ID, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEAM_NOT_FOUND"));
    }
}
