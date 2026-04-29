package com.sportsify.common.swagger;

import java.lang.annotation.*;

/**
 * 모든 API에 공통으로 붙는 400(유효성) / 500 응답.
 * 400 example은 각 API의 @SwaggerApiResponse.BadRequest 또는 @SwaggerApiResponse.InvalidPriority로 오버라이드한다.
 */
@Target({ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SwaggerApiResponse.BadRequest
@SwaggerApiResponse.InternalError
public @interface CommonApiResponses {
}
