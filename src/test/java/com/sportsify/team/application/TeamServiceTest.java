package com.sportsify.team.application;

import com.sportsify.common.exception.BusinessException;
import com.sportsify.common.exception.ErrorCode;
import com.sportsify.team.application.dto.TeamResult;
import com.sportsify.team.application.service.TeamService;
import com.sportsify.team.domain.model.SportType;
import com.sportsify.team.domain.model.Team;
import com.sportsify.team.domain.repository.TeamRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class TeamServiceTest {

    @InjectMocks
    private TeamService teamService;

    @Mock
    private TeamRepository teamRepository;

    // ──────────────────────── getTeams ────────────────────────

    @Test
    @DisplayName("필터 없이 전체 팀 목록을 반환한다")
    void getTeams_전체조회() {
        given(teamRepository.findAll()).willReturn(List.of(
                team(1L, "KIA 타이거즈", SportType.BASEBALL, true),
                team(2L, "FC 서울", SportType.FOOTBALL, true)
        ));

        List<TeamResult> result = teamService.getTeams(null, null);

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("sportType 필터만 적용 시 해당 종목 팀만 반환한다")
    void getTeams_종목필터() {
        given(teamRepository.findBySportType(SportType.BASEBALL)).willReturn(List.of(
                team(1L, "KIA 타이거즈", SportType.BASEBALL, true)
        ));

        List<TeamResult> result = teamService.getTeams("BASEBALL", null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).sportType()).isEqualTo("BASEBALL");
    }

    @Test
    @DisplayName("isActive 필터만 적용 시 활성 팀만 반환한다")
    void getTeams_활성필터() {
        given(teamRepository.findByActive(true)).willReturn(List.of(
                team(1L, "KIA 타이거즈", SportType.BASEBALL, true)
        ));

        List<TeamResult> result = teamService.getTeams(null, true);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).active()).isTrue();
    }

    @Test
    @DisplayName("sportType과 isActive 모두 적용 시 교집합 결과를 반환한다")
    void getTeams_종목_활성_복합필터() {
        given(teamRepository.findBySportTypeAndActive(SportType.FOOTBALL, true)).willReturn(List.of(
                team(2L, "FC 서울", SportType.FOOTBALL, true)
        ));

        List<TeamResult> result = teamService.getTeams("FOOTBALL", true);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).sportType()).isEqualTo("FOOTBALL");
    }

    @Test
    @DisplayName("유효하지 않은 sportType 입력 시 INVALID_INPUT 예외가 발생한다")
    void getTeams_잘못된종목_예외() {
        assertThatThrownBy(() -> teamService.getTeams("INVALID_SPORT", null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    // ──────────────────────── getTeam ────────────────────────

    @Test
    @DisplayName("팀 ID로 팀 상세 정보를 반환한다")
    void getTeam_상세조회() {
        given(teamRepository.findById(1L)).willReturn(Optional.of(
                team(1L, "KIA 타이거즈", SportType.BASEBALL, true)
        ));

        TeamResult result = teamService.getTeam(1L);

        assertThat(result.teamId()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("KIA 타이거즈");
    }

    @Test
    @DisplayName("존재하지 않는 팀 ID 조회 시 TEAM_NOT_FOUND 예외가 발생한다")
    void getTeam_없는팀_예외() {
        given(teamRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> teamService.getTeam(99L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.TEAM_NOT_FOUND);
    }

    // ──────────────────────── 픽스처 ────────────────────────

    private Team team(Long id, String name, SportType sportType, boolean active) {
        Team team = Team.createForTest(name, "단축", sportType);
        try {
            var field = Team.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(team, id);
            var activeField = Team.class.getDeclaredField("active");
            activeField.setAccessible(true);
            activeField.set(team, active);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return team;
    }
}
