package com.sportsify.team.presentation;

import com.sportsify.common.exception.BusinessException;
import com.sportsify.common.exception.ErrorCode;
import com.sportsify.team.application.dto.TeamResult;
import com.sportsify.team.application.service.TeamService;
import com.sportsify.team.presentation.controller.TeamController;
import com.sportsify.support.WebMvcTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TeamController.class)
class TeamControllerApiTest extends WebMvcTestSupport {

    @MockitoBean
    private TeamService teamService;

    // ──────────────────────── GET /api/teams ────────────────────────

    @Test
    @DisplayName("GET /api/teams — 200 전체 팀 목록 조회")
    void 팀_목록_조회() throws Exception {
        given(teamService.getTeams(null, null)).willReturn(List.of(
                new TeamResult(1L, "KIA 타이거즈", "KIA", "BASEBALL", null, true),
                new TeamResult(2L, "FC 서울", "서울", "FOOTBALL", null, true)
        ));

        mockMvc.perform(get("/api/teams"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    @DisplayName("GET /api/teams — 200 종목 필터 조회")
    void 팀_목록_종목필터() throws Exception {
        given(teamService.getTeams("BASEBALL", null)).willReturn(List.of(
                new TeamResult(1L, "KIA 타이거즈", "KIA", "BASEBALL", null, true)
        ));

        mockMvc.perform(get("/api/teams").param("sportType", "BASEBALL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].sportType").value("BASEBALL"));
    }

    @Test
    @DisplayName("GET /api/teams — 400 유효하지 않은 sportType")
    void 팀_목록_잘못된종목() throws Exception {
        given(teamService.getTeams("INVALID", null))
                .willThrow(new BusinessException(ErrorCode.INVALID_INPUT));

        mockMvc.perform(get("/api/teams").param("sportType", "INVALID"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    // ──────────────────────── GET /api/teams/{teamId} ────────────────────────

    @Test
    @DisplayName("GET /api/teams/{teamId} — 200 팀 상세 조회")
    void 팀_상세_조회() throws Exception {
        given(teamService.getTeam(1L)).willReturn(
                new TeamResult(1L, "KIA 타이거즈", "KIA", "BASEBALL", null, true)
        );

        mockMvc.perform(get("/api/teams/{teamId}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teamId").value(1))
                .andExpect(jsonPath("$.data.name").value("KIA 타이거즈"));
    }

    @Test
    @DisplayName("GET /api/teams/{teamId} — 404 존재하지 않는 팀")
    void 팀_상세_없는팀() throws Exception {
        given(teamService.getTeam(99L))
                .willThrow(new BusinessException(ErrorCode.TEAM_NOT_FOUND));

        mockMvc.perform(get("/api/teams/{teamId}", 99L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("TEAM_NOT_FOUND"));
    }
}
