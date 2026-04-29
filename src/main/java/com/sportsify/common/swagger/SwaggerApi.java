package com.sportsify.common.swagger;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import java.lang.annotation.*;

/**
 * @Operation + 200 응답을 하나로 축약한 어노테이션.
 * 성공 응답 스키마는 메서드 반환 타입 ResponseEntity<CommonResponse<T>>에서 Springdoc이 자동 생성한다.
 * summary/description/responseDescription은 OperationCustomizer가 실제 값으로 교체한다.
 * 204 등 본문 없는 응답은 responseCode를 지정한다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Operation(summary = "")
@ApiResponses(@ApiResponse(responseCode = "200", description = ""))
public @interface SwaggerApi {
    String summary();
    String description() default "";
    String responseCode() default "200";
    String responseDescription() default "성공";
}
