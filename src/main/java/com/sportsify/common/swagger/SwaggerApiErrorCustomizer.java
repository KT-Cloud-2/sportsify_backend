package com.sportsify.common.swagger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportsify.common.exception.ErrorCode;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class SwaggerApiErrorCustomizer implements OperationCustomizer {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        hideAuthenticationPrincipalParams(operation, handlerMethod);
        applySwaggerApi(operation, handlerMethod);
        applyErrors(operation, handlerMethod);
        return operation;
    }

    private void applySwaggerApi(Operation operation, HandlerMethod handlerMethod) {
        SwaggerApi swaggerApi = handlerMethod.getMethodAnnotation(SwaggerApi.class);
        if (swaggerApi == null) {
            return;
        }

        operation.setSummary(swaggerApi.summary());
        if (!swaggerApi.description().isBlank()) {
            operation.setDescription(swaggerApi.description());
        }

        ApiResponses responses = operation.getResponses();
        if (responses == null) {
            responses = new ApiResponses();
            operation.setResponses(responses);
        }

        String targetCode = swaggerApi.responseCode();

        // Springdoc이 반환 타입에서 자동 생성한 200 응답을 가져와 description 적용
        // 204 등 다른 코드면 200을 제거하고 해당 코드로 이동 (content 없음)
        if ("200".equals(targetCode)) {
            ApiResponse existing = responses.computeIfAbsent("200", k -> new ApiResponse());
            existing.setDescription(swaggerApi.responseDescription());
        } else {
            responses.remove("200");
            ApiResponse noContent = new ApiResponse();
            noContent.setDescription(swaggerApi.responseDescription());
            responses.put(targetCode, noContent);
        }
    }

    private void applyErrors(Operation operation, HandlerMethod handlerMethod) {
        List<SwaggerApiError> errors = collectErrors(handlerMethod);
        if (errors.isEmpty()) {
            return;
        }

        ApiResponses responses = operation.getResponses();
        if (responses == null) {
            responses = new ApiResponses();
            operation.setResponses(responses);
        }

        for (SwaggerApiError error : errors) {
            ErrorCode errorCode = error.value();
            String statusCode = String.valueOf(errorCode.getHttpStatus().value());
            ApiResponse apiResponse = responses.computeIfAbsent(statusCode, k -> new ApiResponse());
            apiResponse.setDescription(errorCode.getMessage());
            apiResponse.setContent(buildErrorContent(errorCode, error.detail()));
        }
    }

    private List<SwaggerApiError> collectErrors(HandlerMethod handlerMethod) {
        List<SwaggerApiError> errors = new ArrayList<>();

        if (hasRequestBody(handlerMethod)) {
            errors.add(invalidInputAnnotation());
        }

        SwaggerApiError single = handlerMethod.getMethodAnnotation(SwaggerApiError.class);
        SwaggerApiErrors container = handlerMethod.getMethodAnnotation(SwaggerApiErrors.class);

        if (single != null) {
            errors.add(single);
        } else if (container != null) {
            errors.addAll(Arrays.asList(container.value()));
        }

        return errors;
    }

    private boolean hasRequestBody(HandlerMethod handlerMethod) {
        for (Parameter parameter : handlerMethod.getMethod().getParameters()) {
            if (parameter.isAnnotationPresent(RequestBody.class)) {
                return true;
            }
        }
        return false;
    }

    private void hideAuthenticationPrincipalParams(Operation operation, HandlerMethod handlerMethod) {
        if (operation.getParameters() == null) {
            return;
        }
        for (Parameter parameter : handlerMethod.getMethod().getParameters()) {
            if (parameter.isAnnotationPresent(org.springframework.security.core.annotation.AuthenticationPrincipal.class)) {
                operation.getParameters().removeIf(p -> p.getName().equals(parameter.getName()));
            }
        }
    }

    private SwaggerApiError invalidInputAnnotation() {
        return new SwaggerApiError() {
            public Class<SwaggerApiError> annotationType() { return SwaggerApiError.class; }
            public ErrorCode value() { return ErrorCode.INVALID_INPUT; }
            public String detail() { return ""; }
        };
    }

    private Content buildErrorContent(ErrorCode errorCode, String detail) {
        Schema<String> schema = new Schema<>();
        schema.setName("ErrorResponse");

        io.swagger.v3.oas.models.examples.Example example = new io.swagger.v3.oas.models.examples.Example();
        example.setValue(parseJson(errorCode.toExampleJson(detail)));

        MediaType mediaType = new MediaType();
        mediaType.setSchema(schema);
        mediaType.addExamples(errorCode.getCode(), example);

        Content content = new Content();
        content.addMediaType("application/json", mediaType);
        return content;
    }

    private Object parseJson(String json) {
        try {
            return OBJECT_MAPPER.readValue(json, Object.class);
        } catch (Exception e) {
            return json;
        }
    }
}
