package com.sportsify.member.presentation.api;

import com.sportsify.common.exception.ErrorCode;
import com.sportsify.common.response.CommonResponse;
import com.sportsify.common.swagger.AuthRequiredApi;
import com.sportsify.common.swagger.CommonApiResponses;
import com.sportsify.common.swagger.SwaggerApi;
import com.sportsify.common.swagger.SwaggerApiError;
import com.sportsify.member.presentation.dto.*;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@Tag(name = "Member", description = "회원 정보 API")
@AuthRequiredApi
@CommonApiResponses
public interface MemberApi {

    @SwaggerApi(summary = "내 정보 조회", description = "로그인한 회원의 기본 정보를 반환합니다.")
    @SwaggerApiError(ErrorCode.MEMBER_NOT_FOUND)
    ResponseEntity<CommonResponse<MemberResponse>> getMe(Long memberId);

    @SwaggerApi(summary = "닉네임 수정", description = "닉네임을 변경합니다. 2~20자, 중복 불가.")
    @SwaggerApiError(ErrorCode.NICKNAME_DUPLICATE)
    ResponseEntity<CommonResponse<UpdateNicknameResponse>> updateNickname(
            Long memberId,
            @RequestBody UpdateNicknameRequest request
    );

    @SwaggerApi(summary = "회원 탈퇴", description = "회원 상태를 WITHDRAWN으로 변경합니다.", responseCode = "204", responseDescription = "성공 (본문 없음)")
    @SwaggerApiError(ErrorCode.MEMBER_NOT_FOUND)
    ResponseEntity<Void> withdraw(Long memberId);

    @SwaggerApi(
            summary = "선호 팀 추가",
            description = "선호 팀을 추가합니다. priority 미지정 시 마지막 순위 + 1로 자동 설정됩니다.")
    @SwaggerApiError(ErrorCode.TEAM_NOT_FOUND)
    @SwaggerApiError(ErrorCode.FAVORITE_TEAM_ALREADY_EXISTS)
    ResponseEntity<CommonResponse<FavoriteTeamResponse>> addFavoriteTeam(
            Long memberId,
            @RequestBody AddFavoriteTeamRequest request
    );

    @SwaggerApi(summary = "선호 팀 목록 조회", description = "등록된 선호 팀을 priority 오름차순으로 반환합니다.")
    ResponseEntity<CommonResponse<List<FavoriteTeamResponse>>> getFavoriteTeams(Long memberId);

    @SwaggerApi(summary = "선호 팀 우선순위 수정",
            description = "선호 팀의 우선순위를 변경합니다. 1 이상, 등록된 팀 수 이하여야 합니다.")
    @SwaggerApiError(ErrorCode.INVALID_PRIORITY)
    @SwaggerApiError(ErrorCode.FAVORITE_TEAM_NOT_FOUND)
    ResponseEntity<CommonResponse<FavoriteTeamResponse>> updateFavoriteTeamPriority(
            Long memberId,
            @Parameter(description = "우선순위를 변경할 팀 ID (teams.id 기준)") @PathVariable Long teamId,
            @RequestBody UpdatePriorityRequest request
    );

    @SwaggerApi(summary = "선호 팀 삭제", description = "선호 팀을 삭제합니다. teamId는 teams.id 기준입니다.", responseCode = "204", responseDescription = "성공 (본문 없음)")
    @SwaggerApiError(ErrorCode.FAVORITE_TEAM_NOT_FOUND)
    ResponseEntity<Void> removeFavoriteTeam(
            Long memberId,
            @Parameter(description = "삭제할 팀 ID (teams.id 기준)") @PathVariable Long teamId
    );
}
