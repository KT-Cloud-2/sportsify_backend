1. 인터페이스에만 문서 어노테이션 배치
    - `@SwaggerApi`, `@SwaggerApiError`, `@AuthRequiredApi` 등 OpenAPI 관련 어노테이션은 `*Api` 인터페이스에만 둔다
    - 구현체 클래스에는 순수 비즈니스 로직만 남겨서 가독성과 재사용성 확보

2. 에러 응답은 @SwaggerApi 인라인 또는 @SwaggerApiError로 선언
    - 단일 에러: `@SwaggerApi(error = ErrorCode.XXX)`
    - 복수 에러: `@SwaggerApi(errors = {ErrorCode.XXX, ErrorCode.YYY})`
    - `@RequestBody` 파라미터가 있으면 `400 INVALID_INPUT`은 자동 추가 — 중복 선언 불필요

3. 성공 응답은 도메인 DTO 직접 반환
    - `ResponseEntity<T>` 형태로 반환, 래핑 클래스(`CommonResponse`) 없음
    - 본문 없는 응답은 `ResponseEntity<Void>` + `responseCode = "204"` 지정

4. DTO/응답 모델에만 스키마 어노테이션 붙이기
    - Domain 모델과 API 스펙을 완전히 분리
    - 문서 수정 시 비즈니스 로직(도메인) 오염 방지

5. Springdoc 기본 흐름을 그대로 활용
    - `@AuthenticationPrincipal` 파라미터는 자동 숨김 (springdoc-openapi-security 내장)
    - OpenAPI spec은 `/v3/api-docs`, UI는 `/docs.html` (Swagger UI + Redoc 탭)
    - 외부 공유용 standalone HTML은 `./gradlew generateApiDocs` → `build/api-spec/docs-export.html`
