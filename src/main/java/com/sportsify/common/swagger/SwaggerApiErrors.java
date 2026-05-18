package com.sportsify.common.swagger;

import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SwaggerApiErrors {
    SwaggerApiError[] value();
}
