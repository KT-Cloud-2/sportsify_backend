package com.sportsify.member.presentation;

import com.epages.restdocs.apispec.Schema;
import com.sportsify.common.exception.BusinessException;
import com.sportsify.common.exception.ErrorCode;
import com.sportsify.member.application.dto.FavoriteTeamResult;
import com.sportsify.member.application.dto.MemberResult;
import com.sportsify.member.application.service.MemberService;
import com.sportsify.member.presentation.dto.AddFavoriteTeamRequest;
import com.sportsify.member.presentation.dto.UpdateNicknameRequest;
import com.sportsify.member.presentation.dto.UpdatePriorityRequest;
import com.sportsify.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.resourceDetails;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.doNothing;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.*;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MemberControllerApiTest extends ApiTestSupport {

    @MockitoBean
    private MemberService memberService;

    private static final Long TEST_MEMBER_ID = 1L;

    private static org.springframework.restdocs.payload.FieldDescriptor[] errorResponseFields() {
        return new org.springframework.restdocs.payload.FieldDescriptor[]{
                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부 (`false`)"),
                fieldWithPath("data").type(JsonFieldType.NULL).description("데이터 없음"),
                fieldWithPath("error.code").type(JsonFieldType.STRING).description("에러 코드"),
                fieldWithPath("error.message").type(JsonFieldType.STRING).description("에러 메시지"),
                fieldWithPath("error.detail").type(JsonFieldType.STRING).description("상세 정보").optional(),
                fieldWithPath("timestamp").type(JsonFieldType.STRING).description("응답 시각")
        };
    }

    // ──────────────────────── GET /api/members/me ────────────────────────

    @Test
    @DisplayName("GET /api/members/me — 200 내 정보 조회 성공")
    void 내정보_조회_성공() throws Exception {
        MemberResult memberResult = new MemberResult(
                TEST_MEMBER_ID, "test@example.com", "응원왕", LocalDateTime.of(2026, 1, 1, 0, 0)
        );
        given(memberService.getMe(TEST_MEMBER_ID)).willReturn(memberResult);

        mockMvc.perform(get("/api/members/me")
                        .header("Authorization", bearerToken(TEST_MEMBER_ID, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.memberId").value(1))
                .andExpect(jsonPath("$.data.nickname").value("응원왕"))
                .andDo(document("내정보-조회",
                        resourceDetails()
                                .tag("Member")
                                .summary("내 정보 조회")
                                .description("로그인한 회원의 기본 정보를 반환합니다.")
                                .responseSchema(Schema.schema("MemberResponse")),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부"),
                                fieldWithPath("data.memberId").type(JsonFieldType.NUMBER).description("회원 ID"),
                                fieldWithPath("data.email").type(JsonFieldType.STRING).description("이메일"),
                                fieldWithPath("data.nickname").type(JsonFieldType.STRING).description("닉네임"),
                                fieldWithPath("data.createdAt").type(JsonFieldType.STRING).description("가입 시각 (ISO8601)"),
                                fieldWithPath("error").type(JsonFieldType.NULL).description("에러 정보"),
                                fieldWithPath("timestamp").type(JsonFieldType.STRING).description("응답 시각")
                        )
                ));
    }

    @Test
    @DisplayName("GET /api/members/me — 401 인증 없음")
    void 내정보_조회_인증없음() throws Exception {
        mockMvc.perform(get("/api/members/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/members/me — 404 탈퇴 회원")
    void 내정보_조회_탈퇴회원() throws Exception {
        given(memberService.getMe(TEST_MEMBER_ID))
                .willThrow(new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        mockMvc.perform(get("/api/members/me")
                        .header("Authorization", bearerToken(TEST_MEMBER_ID, "USER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MEMBER_NOT_FOUND"))
                .andDo(document("내정보-조회-404",
                        resourceDetails().tag("Member"),
                        responseFields(errorResponseFields())
                ));
    }

    // ──────────────────────── PATCH /api/members/me/nickname ────────────────────────

    @Test
    @DisplayName("PATCH /api/members/me/nickname — 200 닉네임 수정 성공")
    void 닉네임_수정_성공() throws Exception {
        MemberResult updated = new MemberResult(TEST_MEMBER_ID, "test@example.com", "새닉네임", LocalDateTime.of(2026, 1, 1, 0, 0));
        given(memberService.updateNickname(TEST_MEMBER_ID, "새닉네임")).willReturn(updated);

        mockMvc.perform(patch("/api/members/me/nickname")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateNicknameRequest("새닉네임")))
                        .header("Authorization", bearerToken(TEST_MEMBER_ID, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("새닉네임"))
                .andDo(document("닉네임-수정",
                        resourceDetails()
                                .tag("Member")
                                .summary("내 정보 수정 (닉네임)")
                                .description("닉네임을 변경합니다. 2~20자, 중복 불가.")
                                .requestSchema(Schema.schema("UpdateNicknameRequest"))
                                .responseSchema(Schema.schema("UpdateNicknameResponse")),
                        requestFields(
                                fieldWithPath("nickname").type(JsonFieldType.STRING).description("변경할 닉네임 (2~20자)")
                        ),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부"),
                                fieldWithPath("data.memberId").type(JsonFieldType.NUMBER).description("회원 ID"),
                                fieldWithPath("data.nickname").type(JsonFieldType.STRING).description("변경된 닉네임"),
                                fieldWithPath("error").type(JsonFieldType.NULL).description("에러 정보"),
                                fieldWithPath("timestamp").type(JsonFieldType.STRING).description("응답 시각")
                        )
                ));
    }

    @Test
    @DisplayName("PATCH /api/members/me/nickname — 400 닉네임 유효성 실패")
    void 닉네임_수정_유효성실패() throws Exception {
        mockMvc.perform(patch("/api/members/me/nickname")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateNicknameRequest("A")))
                        .header("Authorization", bearerToken(TEST_MEMBER_ID, "USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
                .andDo(document("닉네임-수정-400",
                        resourceDetails().tag("Member"),
                        responseFields(errorResponseFields())
                ));
    }

    @Test
    @DisplayName("PATCH /api/members/me/nickname — 409 닉네임 중복")
    void 닉네임_수정_중복() throws Exception {
        given(memberService.updateNickname(TEST_MEMBER_ID, "중복닉네임"))
                .willThrow(new BusinessException(ErrorCode.NICKNAME_DUPLICATE));

        mockMvc.perform(patch("/api/members/me/nickname")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateNicknameRequest("중복닉네임")))
                        .header("Authorization", bearerToken(TEST_MEMBER_ID, "USER")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("NICKNAME_DUPLICATE"))
                .andDo(document("닉네임-수정-409",
                        resourceDetails().tag("Member"),
                        responseFields(errorResponseFields())
                ));
    }

    // ──────────────────────── DELETE /api/members/me ────────────────────────

    @Test
    @DisplayName("DELETE /api/members/me — 204 회원 탈퇴 성공")
    void 회원_탈퇴_성공() throws Exception {
        doNothing().when(memberService).withdraw(TEST_MEMBER_ID);

        mockMvc.perform(delete("/api/members/me")
                        .header("Authorization", bearerToken(TEST_MEMBER_ID, "USER")))
                .andExpect(status().isNoContent())
                .andDo(document("회원-탈퇴",
                        resourceDetails()
                                .tag("Member")
                                .summary("회원 탈퇴")
                                .description("회원 상태를 WITHDRAWN으로 변경합니다.")
                ));
    }

    @Test
    @DisplayName("DELETE /api/members/me — 404 존재하지 않는 회원")
    void 회원_탈퇴_회원없음() throws Exception {
        willThrow(new BusinessException(ErrorCode.MEMBER_NOT_FOUND))
                .given(memberService).withdraw(TEST_MEMBER_ID);

        mockMvc.perform(delete("/api/members/me")
                        .header("Authorization", bearerToken(TEST_MEMBER_ID, "USER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MEMBER_NOT_FOUND"))
                .andDo(document("회원-탈퇴-404",
                        resourceDetails().tag("Member"),
                        responseFields(errorResponseFields())
                ));
    }

    // ──────────────────────── POST /api/members/me/favorite-teams ────────────────────────

    @Test
    @DisplayName("POST /api/members/me/favorite-teams — 200 선호 팀 추가 성공")
    void 선호팀_추가_성공() throws Exception {
        FavoriteTeamResult result = new FavoriteTeamResult(10L, 3L, "KIA 타이거즈", "KIA", "BASEBALL", 1);
        given(memberService.addFavoriteTeam(TEST_MEMBER_ID, 3L, 1)).willReturn(result);

        mockMvc.perform(post("/api/members/me/favorite-teams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddFavoriteTeamRequest(3L, 1)))
                        .header("Authorization", bearerToken(TEST_MEMBER_ID, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teamName").value("KIA 타이거즈"))
                .andDo(document("선호팀-추가",
                        resourceDetails()
                                .tag("FavoriteTeam")
                                .summary("선호 팀 추가")
                                .description("선호 팀을 추가합니다. priority 미지정 시 마지막 순위 + 1로 자동 설정됩니다.")
                                .requestSchema(Schema.schema("AddFavoriteTeamRequest"))
                                .responseSchema(Schema.schema("FavoriteTeamResponse")),
                        requestFields(
                                fieldWithPath("teamId").type(JsonFieldType.NUMBER).description("추가할 팀 ID"),
                                fieldWithPath("priority").type(JsonFieldType.NUMBER).description("선호 순위 (선택, 미지정 시 자동 할당)").optional()
                        ),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부"),
                                fieldWithPath("data.favoriteTeamId").type(JsonFieldType.NUMBER).description("선호 팀 레코드 ID"),
                                fieldWithPath("data.teamId").type(JsonFieldType.NUMBER).description("팀 ID"),
                                fieldWithPath("data.teamName").type(JsonFieldType.STRING).description("팀 이름"),
                                fieldWithPath("data.shortName").type(JsonFieldType.STRING).description("팀 약칭").optional(),
                                fieldWithPath("data.sportType").type(JsonFieldType.STRING).description("종목 (BASEBALL | FOOTBALL | BASKETBALL)"),
                                fieldWithPath("data.priority").type(JsonFieldType.NUMBER).description("선호 순위"),
                                fieldWithPath("error").type(JsonFieldType.NULL).description("에러 정보"),
                                fieldWithPath("timestamp").type(JsonFieldType.STRING).description("응답 시각")
                        )
                ));
    }

    @Test
    @DisplayName("POST /api/members/me/favorite-teams — 409 이미 등록된 팀")
    void 선호팀_추가_중복() throws Exception {
        given(memberService.addFavoriteTeam(TEST_MEMBER_ID, 3L, null))
                .willThrow(new BusinessException(ErrorCode.FAVORITE_TEAM_ALREADY_EXISTS));

        mockMvc.perform(post("/api/members/me/favorite-teams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddFavoriteTeamRequest(3L, null)))
                        .header("Authorization", bearerToken(TEST_MEMBER_ID, "USER")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("FAVORITE_TEAM_ALREADY_EXISTS"))
                .andDo(document("선호팀-추가-409",
                        resourceDetails().tag("FavoriteTeam"),
                        responseFields(errorResponseFields())
                ));
    }

    @Test
    @DisplayName("POST /api/members/me/favorite-teams — 404 존재하지 않는 팀")
    void 선호팀_추가_팀없음() throws Exception {
        given(memberService.addFavoriteTeam(TEST_MEMBER_ID, 99L, null))
                .willThrow(new BusinessException(ErrorCode.TEAM_NOT_FOUND));

        mockMvc.perform(post("/api/members/me/favorite-teams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddFavoriteTeamRequest(99L, null)))
                        .header("Authorization", bearerToken(TEST_MEMBER_ID, "USER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("TEAM_NOT_FOUND"))
                .andDo(document("선호팀-추가-404",
                        resourceDetails().tag("FavoriteTeam"),
                        responseFields(errorResponseFields())
                ));
    }

    // ──────────────────────── GET /api/members/me/favorite-teams ────────────────────────

    @Test
    @DisplayName("GET /api/members/me/favorite-teams — 200 선호 팀 목록 조회 성공")
    void 선호팀_목록_조회() throws Exception {
        List<FavoriteTeamResult> results = List.of(
                new FavoriteTeamResult(10L, 3L, "KIA 타이거즈", "KIA", "BASEBALL", 1),
                new FavoriteTeamResult(11L, 5L, "두산 베어스", "두산", "BASEBALL", 2)
        );
        given(memberService.getFavoriteTeams(TEST_MEMBER_ID)).willReturn(results);

        mockMvc.perform(get("/api/members/me/favorite-teams")
                        .header("Authorization", bearerToken(TEST_MEMBER_ID, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andDo(document("선호팀-목록",
                        resourceDetails()
                                .tag("FavoriteTeam")
                                .summary("선호 팀 목록 조회")
                                .description("등록된 선호 팀을 priority 오름차순으로 반환합니다."),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부"),
                                fieldWithPath("data[].favoriteTeamId").type(JsonFieldType.NUMBER).description("선호 팀 레코드 ID"),
                                fieldWithPath("data[].teamId").type(JsonFieldType.NUMBER).description("팀 ID"),
                                fieldWithPath("data[].teamName").type(JsonFieldType.STRING).description("팀 이름"),
                                fieldWithPath("data[].shortName").type(JsonFieldType.STRING).description("팀 약칭").optional(),
                                fieldWithPath("data[].sportType").type(JsonFieldType.STRING).description("종목"),
                                fieldWithPath("data[].priority").type(JsonFieldType.NUMBER).description("선호 순위"),
                                fieldWithPath("error").type(JsonFieldType.NULL).description("에러 정보"),
                                fieldWithPath("timestamp").type(JsonFieldType.STRING).description("응답 시각")
                        )
                ));
    }

    // ──────────────────────── PATCH .../favorite-teams/{teamId}/priority ────────────────────────

    @Test
    @DisplayName("PATCH /api/members/me/favorite-teams/{teamId}/priority — 200 우선순위 수정 성공")
    void 선호팀_우선순위_수정() throws Exception {
        FavoriteTeamResult updated = new FavoriteTeamResult(10L, 3L, "KIA 타이거즈", "KIA", "BASEBALL", 2);
        given(memberService.updateFavoriteTeamPriority(TEST_MEMBER_ID, 3L, 2)).willReturn(updated);

        mockMvc.perform(patch("/api/members/me/favorite-teams/{teamId}/priority", 3L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdatePriorityRequest(2)))
                        .header("Authorization", bearerToken(TEST_MEMBER_ID, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.priority").value(2))
                .andDo(document("선호팀-우선순위-수정",
                        resourceDetails()
                                .tag("FavoriteTeam")
                                .summary("선호 팀 우선순위 수정")
                                .description("선호 팀의 우선순위를 변경합니다. 1 이상, 등록된 팀 수 이하여야 합니다."),
                        pathParameters(
                                parameterWithName("teamId").description("우선순위를 변경할 팀 ID (teams.id 기준)")
                        ),
                        requestFields(
                                fieldWithPath("priority").type(JsonFieldType.NUMBER).description("변경할 우선순위 (1 이상)")
                        ),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부"),
                                fieldWithPath("data.favoriteTeamId").type(JsonFieldType.NUMBER).description("선호 팀 레코드 ID"),
                                fieldWithPath("data.teamId").type(JsonFieldType.NUMBER).description("팀 ID"),
                                fieldWithPath("data.teamName").type(JsonFieldType.STRING).description("팀 이름"),
                                fieldWithPath("data.shortName").type(JsonFieldType.STRING).description("팀 약칭").optional(),
                                fieldWithPath("data.sportType").type(JsonFieldType.STRING).description("종목"),
                                fieldWithPath("data.priority").type(JsonFieldType.NUMBER).description("변경된 우선순위"),
                                fieldWithPath("error").type(JsonFieldType.NULL).description("에러 정보"),
                                fieldWithPath("timestamp").type(JsonFieldType.STRING).description("응답 시각")
                        )
                ));
    }

    @Test
    @DisplayName("PATCH /api/members/me/favorite-teams/{teamId}/priority — 400 우선순위 범위 초과")
    void 선호팀_우선순위_수정_범위초과() throws Exception {
        given(memberService.updateFavoriteTeamPriority(TEST_MEMBER_ID, 3L, 99))
                .willThrow(new BusinessException(ErrorCode.INVALID_PRIORITY));

        mockMvc.perform(patch("/api/members/me/favorite-teams/{teamId}/priority", 3L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdatePriorityRequest(99)))
                        .header("Authorization", bearerToken(TEST_MEMBER_ID, "USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_PRIORITY"))
                .andDo(document("선호팀-우선순위-수정-400",
                        resourceDetails().tag("FavoriteTeam"),
                        pathParameters(
                                parameterWithName("teamId").description("팀 ID")
                        ),
                        responseFields(errorResponseFields())
                ));
    }

    @Test
    @DisplayName("PATCH /api/members/me/favorite-teams/{teamId}/priority — 404 등록되지 않은 팀")
    void 선호팀_우선순위_수정_미등록팀() throws Exception {
        given(memberService.updateFavoriteTeamPriority(TEST_MEMBER_ID, 99L, 1))
                .willThrow(new BusinessException(ErrorCode.FAVORITE_TEAM_NOT_FOUND));

        mockMvc.perform(patch("/api/members/me/favorite-teams/{teamId}/priority", 99L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdatePriorityRequest(1)))
                        .header("Authorization", bearerToken(TEST_MEMBER_ID, "USER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("FAVORITE_TEAM_NOT_FOUND"))
                .andDo(document("선호팀-우선순위-수정-404",
                        resourceDetails().tag("FavoriteTeam"),
                        pathParameters(
                                parameterWithName("teamId").description("팀 ID")
                        ),
                        responseFields(errorResponseFields())
                ));
    }

    // ──────────────────────── DELETE .../favorite-teams/{teamId} ────────────────────────

    @Test
    @DisplayName("DELETE /api/members/me/favorite-teams/{teamId} — 204 선호 팀 삭제 성공")
    void 선호팀_삭제_성공() throws Exception {
        doNothing().when(memberService).removeFavoriteTeam(TEST_MEMBER_ID, 3L);

        mockMvc.perform(delete("/api/members/me/favorite-teams/{teamId}", 3L)
                        .header("Authorization", bearerToken(TEST_MEMBER_ID, "USER")))
                .andExpect(status().isNoContent())
                .andDo(document("선호팀-삭제",
                        resourceDetails()
                                .tag("FavoriteTeam")
                                .summary("선호 팀 삭제")
                                .description("선호 팀을 삭제합니다. teamId는 teams.id 기준입니다."),
                        pathParameters(
                                parameterWithName("teamId").description("삭제할 팀 ID (teams.id 기준)")
                        )
                ));
    }

    @Test
    @DisplayName("DELETE /api/members/me/favorite-teams/{teamId} — 404 등록되지 않은 팀")
    void 선호팀_삭제_미등록팀() throws Exception {
        willThrow(new BusinessException(ErrorCode.FAVORITE_TEAM_NOT_FOUND))
                .given(memberService).removeFavoriteTeam(TEST_MEMBER_ID, 99L);

        mockMvc.perform(delete("/api/members/me/favorite-teams/{teamId}", 99L)
                        .header("Authorization", bearerToken(TEST_MEMBER_ID, "USER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("FAVORITE_TEAM_NOT_FOUND"))
                .andDo(document("선호팀-삭제-404",
                        resourceDetails().tag("FavoriteTeam"),
                        pathParameters(
                                parameterWithName("teamId").description("팀 ID")
                        ),
                        responseFields(errorResponseFields())
                ));
    }
}
