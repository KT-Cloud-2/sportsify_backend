package com.sportsify.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Sportsify API")
                        .description("""
                                스포츠 경기 예매 + 팀 응원 채팅 서비스 API

                                ## 공통 에러 응답
                                모든 API는 아래 에러를 공통으로 반환할 수 있습니다.

                                | 상태 코드 | 에러 코드 | 설명 |
                                |-----------|-----------|------|
                                | 500 | `INTERNAL_ERROR` | 서버 내부 오류 |

                                ## 응답 형식
                                모든 응답은 `CommonResponse<T>` 형식으로 감싸집니다.
                                ```json
                                {
                                  "success": true,
                                  "data": { },
                                  "error": null,
                                  "timestamp": "2026-04-29T12:00:00Z"
                                }
                                ```""")
                        .version("0.0.1-SNAPSHOT"))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT Access Token. 헤더: `Authorization: Bearer {token}`")));
    }
}
