package com.sportsify.common.swagger;

import com.sportsify.common.exception.ErrorCode;

import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Repeatable(SwaggerApiErrors.class)
public @interface SwaggerApiError {
    ErrorCode value();
    String detail() default "";
}
