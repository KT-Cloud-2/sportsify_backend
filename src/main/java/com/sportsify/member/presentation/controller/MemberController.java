package com.sportsify.member.presentation.controller;

import com.sportsify.common.response.CommonResponse;
import com.sportsify.member.application.service.MemberService;
import com.sportsify.member.presentation.api.MemberApi;
import com.sportsify.member.presentation.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController implements MemberApi {

    private final MemberService memberService;

    @GetMapping("/me")
    public ResponseEntity<CommonResponse<MemberResponse>> getMe(
            @AuthenticationPrincipal Long memberId
    ) {
        return ResponseEntity.ok(CommonResponse.ok(MemberResponse.from(memberService.getMe(memberId))));
    }

    @PatchMapping("/me/nickname")
    public ResponseEntity<CommonResponse<UpdateNicknameResponse>> updateNickname(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody UpdateNicknameRequest request
    ) {
        return ResponseEntity.ok(CommonResponse.ok(
                UpdateNicknameResponse.from(memberService.updateNickname(memberId, request.nickname()))
        ));
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> withdraw(
            @AuthenticationPrincipal Long memberId
    ) {
        memberService.withdraw(memberId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/me/favorite-teams")
    public ResponseEntity<CommonResponse<FavoriteTeamResponse>> addFavoriteTeam(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody AddFavoriteTeamRequest request
    ) {
        return ResponseEntity.ok(CommonResponse.ok(
                FavoriteTeamResponse.from(memberService.addFavoriteTeam(memberId, request.teamId(), request.priority()))
        ));
    }// 설정변경으로 수정된 맴버 코드

    @GetMapping("/me/favorite-teams")
    public ResponseEntity<CommonResponse<List<FavoriteTeamResponse>>> getFavoriteTeams(
            @AuthenticationPrincipal Long memberId
    ) {
        List<FavoriteTeamResponse> response = memberService.getFavoriteTeams(memberId)
                .stream()
                .map(FavoriteTeamResponse::from)
                .toList();
        return ResponseEntity.ok(CommonResponse.ok(response));
    }

    @PatchMapping("/me/favorite-teams/{teamId}/priority")
    public ResponseEntity<CommonResponse<FavoriteTeamResponse>> updateFavoriteTeamPriority(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long teamId,
            @Valid @RequestBody UpdatePriorityRequest request
    ) {
        return ResponseEntity.ok(CommonResponse.ok(
                FavoriteTeamResponse.from(memberService.updateFavoriteTeamPriority(memberId, teamId, request.priority()))
        ));
    }

    @DeleteMapping("/me/favorite-teams/{teamId}")
    public ResponseEntity<Void> removeFavoriteTeam(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long teamId
    ) {
        memberService.removeFavoriteTeam(memberId, teamId);
        return ResponseEntity.noContent().build();
    }
}
