package com.sportsify.team.presentation.api;

import com.sportsify.common.exception.ErrorCode;
import com.sportsify.common.response.CommonResponse;
import com.sportsify.common.swagger.CommonApiResponses;
import com.sportsify.common.swagger.SwaggerApi;
import com.sportsify.common.swagger.SwaggerApiError;
import com.sportsify.team.presentation.dto.TeamResponse;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Tag(name = "Team", description = "팀 조회 API")
@CommonApiResponses
public interface TeamApi {

    @SwaggerApi(summary = "팀 목록 조회", description = "전체 팀 목록을 조회합니다. sportType, isActive 필터를 조합할 수 있습니다.")
    ResponseEntity<CommonResponse<List<TeamResponse>>> getTeams(
            @Parameter(description = "종목 필터 (BASEBALL | FOOTBALL | BASKETBALL)")
            @RequestParam(required = false) String sportType,
            @Parameter(description = "활성 팀 여부 필터 (기본: 전체)")
            @RequestParam(required = false) Boolean isActive
    );

    @SwaggerApi(summary = "팀 상세 조회", description = "팀 ID로 팀 상세 정보를 조회합니다.")
    @SwaggerApiError(ErrorCode.TEAM_NOT_FOUND)
    ResponseEntity<CommonResponse<TeamResponse>> getTeam(
            @Parameter(description = "팀 ID") @PathVariable Long teamId
    );
}
