package com.sportsify.member.presentation.api;

import com.sportsify.common.response.CommonResponse;
import com.sportsify.common.swagger.AuthRequiredApi;
import com.sportsify.common.swagger.CommonApiResponses;
import com.sportsify.common.swagger.SwaggerApiResponse;
import com.sportsify.member.presentation.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@Tag(name = "Member", description = "회원 정보 API")
@AuthRequiredApi
@CommonApiResponses
public interface MemberApi {

    @Operation(summary = "내 정보 조회", description = "로그인한 회원의 기본 정보를 반환합니다.")
    @SwaggerApiResponse.MemberNotFound
    @ApiResponses(@ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(schema = @Schema(implementation = MemberResponse.class))))
    ResponseEntity<CommonResponse<MemberResponse>> getMe(Long memberId);

    @Operation(summary = "닉네임 수정", description = "닉네임을 변경합니다. 2~20자, 중복 불가.")
    @SwaggerApiResponse.NicknameDuplicate
    @ApiResponses(@ApiResponse(responseCode = "200", description = "수정 성공",
            content = @Content(schema = @Schema(implementation = UpdateNicknameResponse.class))))
    ResponseEntity<CommonResponse<UpdateNicknameResponse>> updateNickname(
            Long memberId,
            @Valid @RequestBody UpdateNicknameRequest request
    );

    @Operation(summary = "회원 탈퇴", description = "회원 상태를 WITHDRAWN으로 변경합니다.")
    @SwaggerApiResponse.MemberNotFound
    @SwaggerApiResponse.NoContent
    ResponseEntity<Void> withdraw(Long memberId);

    @Operation(summary = "선호 팀 추가", description = "선호 팀을 추가합니다. priority 미지정 시 마지막 순위 + 1로 자동 설정됩니다.")
    @SwaggerApiResponse.TeamNotFound
    @SwaggerApiResponse.FavoriteTeamAlreadyExists
    @ApiResponses(@ApiResponse(responseCode = "200", description = "추가 성공",
            content = @Content(schema = @Schema(implementation = FavoriteTeamResponse.class))))
    ResponseEntity<CommonResponse<FavoriteTeamResponse>> addFavoriteTeam(
            Long memberId,
            @Valid @RequestBody AddFavoriteTeamRequest request
    );

    @Operation(summary = "선호 팀 목록 조회", description = "등록된 선호 팀을 priority 오름차순으로 반환합니다.")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "조회 성공"))
    ResponseEntity<CommonResponse<List<FavoriteTeamResponse>>> getFavoriteTeams(Long memberId);

    @Operation(summary = "선호 팀 우선순위 수정", description = "선호 팀의 우선순위를 변경합니다. 1 이상, 등록된 팀 수 이하여야 합니다.")
    @SwaggerApiResponse.InvalidPriority
    @SwaggerApiResponse.FavoriteTeamNotFound
    @ApiResponses(@ApiResponse(responseCode = "200", description = "수정 성공",
            content = @Content(schema = @Schema(implementation = FavoriteTeamResponse.class))))
    ResponseEntity<CommonResponse<FavoriteTeamResponse>> updateFavoriteTeamPriority(
            Long memberId,
            @Parameter(description = "우선순위를 변경할 팀 ID (teams.id 기준)") @PathVariable Long teamId,
            @Valid @RequestBody UpdatePriorityRequest request
    );

    @Operation(summary = "선호 팀 삭제", description = "선호 팀을 삭제합니다. teamId는 teams.id 기준입니다.")
    @SwaggerApiResponse.FavoriteTeamNotFound
    @SwaggerApiResponse.NoContent
    ResponseEntity<Void> removeFavoriteTeam(
            Long memberId,
            @Parameter(description = "삭제할 팀 ID (teams.id 기준)") @PathVariable Long teamId
    );
}
