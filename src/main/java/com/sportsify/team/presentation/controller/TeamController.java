package com.sportsify.team.presentation.controller;

import com.sportsify.team.application.service.TeamService;
import com.sportsify.team.presentation.api.TeamApi;
import com.sportsify.team.presentation.dto.TeamResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class TeamController implements TeamApi {

    private final TeamService teamService;

    @GetMapping
    public ResponseEntity<List<TeamResponse>> getTeams(
            @RequestParam(required = false) String sportType,
            @RequestParam(required = false) Boolean isActive
    ) {
        List<TeamResponse> response = teamService.getTeams(sportType, isActive)
                .stream()
                .map(TeamResponse::from)
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{teamId}")
    public ResponseEntity<TeamResponse> getTeam(
            @PathVariable Long teamId
    ) {
        return ResponseEntity.ok(TeamResponse.from(teamService.getTeam(teamId)));
    }
}
