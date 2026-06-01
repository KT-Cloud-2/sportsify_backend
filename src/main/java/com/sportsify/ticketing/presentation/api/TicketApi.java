package com.sportsify.ticketing.presentation.api;

import com.sportsify.common.swagger.AuthRequiredApi;
import com.sportsify.common.swagger.CommonApiResponses;
import com.sportsify.common.swagger.SwaggerApi;
import com.sportsify.ticketing.presentation.dto.TicketListResponseDto;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "티켓", description = "티켓 조회 API")
@AuthRequiredApi
@CommonApiResponses
public interface TicketApi {

    @SwaggerApi(summary = "내 예매 내역 조회",
            description = "로그인한 회원의 티켓 목록을 페이지네이션으로 조회합니다. page/size 파라미터로 제어합니다.")
    ResponseEntity<TicketListResponseDto> getMyTickets(
            Long memberId,
            @Parameter(description = "페이지 번호 (0부터 시작, 기본 0)")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "페이지 크기 (기본 10, 최대 100)")
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size
    );
}
