package com.sportsify.game.presentation.api;

import com.sportsify.common.swagger.CommonApiResponses;
import com.sportsify.common.swagger.SwaggerApi;
import com.sportsify.common.swagger.SwaggerApiError;
import com.sportsify.game.presentation.dto.*;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.util.List;

import static com.sportsify.common.exception.ErrorCode.*;

@Tag(name = "경기", description = "경기 조회 및 등록 API")
@CommonApiResponses
public interface GameApi {

    @SwaggerApi(summary = "경기 목록 조회", description = "경기 목록을 조회합니다. sportType, teamId, status, from, to 필터를 조합할 수 있습니다.")
    ResponseEntity<List<GameListResponseDto>> getGames(
            @Parameter(description = "종목 필터 (BASEBALL | SOCCER | BASKETBALL 등)")
            @RequestParam(required = false) String sportType,
            @Parameter(description = "특정 팀이 참여한 경기만 필터")
            @RequestParam(required = false) Long teamId,
            @Parameter(description = "경기 상태 필터 (SCHEDULED | ON_SALE | SALE_CLOSED | IN_PROGRESS | FINISHED | CANCELLED)")
            @RequestParam(required = false) String status,
            @Parameter(description = "시작일 이후 필터 (ISO8601)")
            @RequestParam(required = false) LocalDateTime from,
            @Parameter(description = "종료일 이전 필터 (ISO8601)")
            @RequestParam(required = false) LocalDateTime to
    );

    @SwaggerApi(summary = "경기 상세 조회", description = "경기 ID로 상세 정보를 조회합니다. 등급별 좌석 요약 포함.")
    @SwaggerApiError(GAME_NOT_FOUND)
    ResponseEntity<GameDetailResponseDto> getGameDetail(
            @Parameter(description = "경기 ID") @PathVariable Long gameId
    );

    @SwaggerApi(summary = "좌석 목록 조회", description = "해당 경기의 좌석 목록을 조회합니다. grade, status 필터 가능.")
    @SwaggerApiError(GAME_NOT_FOUND)
    ResponseEntity<List<GameSeatListResponseDto>> getGameSeats(
            @Parameter(description = "경기 ID") @PathVariable Long gameId,
            @Parameter(description = "좌석 등급 필터 (VIP | R | S | A | OUTFIELD)")
            @RequestParam(required = false) String grade,
            @Parameter(description = "좌석 상태 필터 (AVAILABLE | RESERVED | SOLD)")
            @RequestParam(required = false) String status
    );

    @SwaggerApi(summary = "경기 등록", description = "새 경기를 등록합니다. saleStartAt/saleEndAt 설정 시 자동 판매 전환이 예약됩니다.",
            responseCode = "201", responseDescription = "경기 생성 성공",
            errors = {STADIUM_NOT_FOUND, TEAM_NOT_FOUND})
    ResponseEntity<GameCreateResponseDto> createGame(
            @RequestBody GameCreateRequestDto request
    );
}
