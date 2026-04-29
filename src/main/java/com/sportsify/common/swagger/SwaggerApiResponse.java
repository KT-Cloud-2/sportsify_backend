package com.sportsify.common.swagger;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import java.lang.annotation.*;

/**
 * 자주 쓰이는 에러 응답을 짧게 선언하는 단축 어노테이션 모음.
 * FQN(io.swagger.v3.oas.annotations.responses.ApiResponse) 반복 제거 목적.
 */
public final class SwaggerApiResponse {

    private SwaggerApiResponse() {}

    // ── 성공 ──────────────────────────────────────────────────

    @Target({ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @ApiResponse(responseCode = "200", description = "성공")
    public @interface Ok {}

    @Target({ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @ApiResponse(responseCode = "204", description = "성공 (본문 없음)")
    public @interface NoContent {}

    // ── 클라이언트 오류 ────────────────────────────────────────

    @Target({ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @ApiResponse(
            responseCode = "400",
            description = "입력값 유효성 실패",
            content = @Content(
                    schema = @Schema(implementation = ErrorResponseSchema.class),
                    examples = @ExampleObject(
                            name = "INVALID_INPUT",
                            value = """
                                    {
                                      "success": false,
                                      "data": null,
                                      "error": {
                                        "code": "INVALID_INPUT",
                                        "message": "입력값이 올바르지 않습니다.",
                                        "detail": "refreshToken: 공백일 수 없습니다"
                                      },
                                      "timestamp": "2026-04-29T12:00:00Z"
                                    }"""
                    )
            )
    )
    public @interface BadRequest {}

    @Target({ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @ApiResponse(
            responseCode = "401",
            description = "인증 실패",
            content = @Content(
                    schema = @Schema(implementation = ErrorResponseSchema.class),
                    examples = @ExampleObject(
                            name = "UNAUTHORIZED",
                            value = """
                                    {
                                      "success": false,
                                      "data": null,
                                      "error": {
                                        "code": "UNAUTHORIZED",
                                        "message": "인증이 필요합니다.",
                                        "detail": null
                                      },
                                      "timestamp": "2026-04-29T12:00:00Z"
                                    }"""
                    )
            )
    )
    public @interface Unauthorized {}

    @Target({ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @ApiResponse(
            responseCode = "401",
            description = "유효하지 않거나 만료된 Refresh Token",
            content = @Content(
                    schema = @Schema(implementation = ErrorResponseSchema.class),
                    examples = @ExampleObject(
                            name = "INVALID_REFRESH_TOKEN",
                            value = """
                                    {
                                      "success": false,
                                      "data": null,
                                      "error": {
                                        "code": "INVALID_REFRESH_TOKEN",
                                        "message": "유효하지 않거나 만료된 리프레시 토큰입니다.",
                                        "detail": null
                                      },
                                      "timestamp": "2026-04-29T12:00:00Z"
                                    }"""
                    )
            )
    )
    public @interface InvalidRefreshToken {}

    @Target({ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @ApiResponse(
            responseCode = "404",
            description = "회원을 찾을 수 없음",
            content = @Content(
                    schema = @Schema(implementation = ErrorResponseSchema.class),
                    examples = @ExampleObject(
                            name = "MEMBER_NOT_FOUND",
                            value = """
                                    {
                                      "success": false,
                                      "data": null,
                                      "error": {
                                        "code": "MEMBER_NOT_FOUND",
                                        "message": "존재하지 않는 회원입니다.",
                                        "detail": null
                                      },
                                      "timestamp": "2026-04-29T12:00:00Z"
                                    }"""
                    )
            )
    )
    public @interface MemberNotFound {}

    @Target({ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @ApiResponse(
            responseCode = "404",
            description = "팀을 찾을 수 없음",
            content = @Content(
                    schema = @Schema(implementation = ErrorResponseSchema.class),
                    examples = @ExampleObject(
                            name = "TEAM_NOT_FOUND",
                            value = """
                                    {
                                      "success": false,
                                      "data": null,
                                      "error": {
                                        "code": "TEAM_NOT_FOUND",
                                        "message": "존재하지 않는 팀입니다.",
                                        "detail": null
                                      },
                                      "timestamp": "2026-04-29T12:00:00Z"
                                    }"""
                    )
            )
    )
    public @interface TeamNotFound {}

    @Target({ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @ApiResponse(
            responseCode = "404",
            description = "선호 팀으로 등록되지 않음",
            content = @Content(
                    schema = @Schema(implementation = ErrorResponseSchema.class),
                    examples = @ExampleObject(
                            name = "FAVORITE_TEAM_NOT_FOUND",
                            value = """
                                    {
                                      "success": false,
                                      "data": null,
                                      "error": {
                                        "code": "FAVORITE_TEAM_NOT_FOUND",
                                        "message": "선호 팀으로 등록되지 않은 팀입니다.",
                                        "detail": null
                                      },
                                      "timestamp": "2026-04-29T12:00:00Z"
                                    }"""
                    )
            )
    )
    public @interface FavoriteTeamNotFound {}

    @Target({ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @ApiResponse(
            responseCode = "409",
            description = "닉네임 중복",
            content = @Content(
                    schema = @Schema(implementation = ErrorResponseSchema.class),
                    examples = @ExampleObject(
                            name = "NICKNAME_DUPLICATE",
                            value = """
                                    {
                                      "success": false,
                                      "data": null,
                                      "error": {
                                        "code": "NICKNAME_DUPLICATE",
                                        "message": "이미 사용 중인 닉네임입니다.",
                                        "detail": null
                                      },
                                      "timestamp": "2026-04-29T12:00:00Z"
                                    }"""
                    )
            )
    )
    public @interface NicknameDuplicate {}

    @Target({ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @ApiResponse(
            responseCode = "409",
            description = "이미 등록된 선호 팀",
            content = @Content(
                    schema = @Schema(implementation = ErrorResponseSchema.class),
                    examples = @ExampleObject(
                            name = "FAVORITE_TEAM_ALREADY_EXISTS",
                            value = """
                                    {
                                      "success": false,
                                      "data": null,
                                      "error": {
                                        "code": "FAVORITE_TEAM_ALREADY_EXISTS",
                                        "message": "이미 등록된 선호 팀입니다.",
                                        "detail": null
                                      },
                                      "timestamp": "2026-04-29T12:00:00Z"
                                    }"""
                    )
            )
    )
    public @interface FavoriteTeamAlreadyExists {}

    @Target({ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @ApiResponse(
            responseCode = "400",
            description = "우선순위 범위 초과",
            content = @Content(
                    schema = @Schema(implementation = ErrorResponseSchema.class),
                    examples = @ExampleObject(
                            name = "INVALID_PRIORITY",
                            value = """
                                    {
                                      "success": false,
                                      "data": null,
                                      "error": {
                                        "code": "INVALID_PRIORITY",
                                        "message": "우선순위 범위가 올바르지 않습니다.",
                                        "detail": null
                                      },
                                      "timestamp": "2026-04-29T12:00:00Z"
                                    }"""
                    )
            )
    )
    public @interface InvalidPriority {}

    @Target({ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @ApiResponse(
            responseCode = "500",
            description = "서버 내부 오류",
            content = @Content(
                    schema = @Schema(implementation = ErrorResponseSchema.class),
                    examples = @ExampleObject(
                            name = "INTERNAL_ERROR",
                            value = """
                                    {
                                      "success": false,
                                      "data": null,
                                      "error": {
                                        "code": "INTERNAL_ERROR",
                                        "message": "서버 내부 오류가 발생했습니다.",
                                        "detail": null
                                      },
                                      "timestamp": "2026-04-29T12:00:00Z"
                                    }"""
                    )
            )
    )
    public @interface InternalError {}
}
