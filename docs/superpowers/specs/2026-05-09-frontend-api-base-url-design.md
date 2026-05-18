# Frontend API Base URL 환경별 구성 설계

## 목표

로컬 개발과 Prod 배포 환경 모두에서 프론트엔드(React + Vite)가 백엔드 API를 올바른 URL로 호출할 수 있도록 환경별 설정을 구성한다.

---

## 범위

- 프론트엔드: Vite 환경변수 파일 (`VITE_API_BASE_URL`)
- 백엔드: Spring Boot `SecurityConfig` CORS 인라인 설정 + yml 환경별 `allowed-origins`

---

## 아키텍처 흐름

```
[Local]
React (Vite :5173) --VITE_API_BASE_URL=http://localhost:8080--> Spring Boot (:8080)

[Prod]
React (S3/CDN) --VITE_API_BASE_URL=https://api.sportsify.com--> ALB --> EC2-1 (:8081/:8082)
```

---

## 프론트엔드 변경

### 환경변수 파일

| 파일 | 값 | 커밋 여부 |
|------|----|-----------|
| `frontend/.env.local` | `VITE_API_BASE_URL=http://localhost:8080` | 금지 (`.gitignore`) |
| `frontend/.env.production` | `VITE_API_BASE_URL=https://api.sportsify.com` | 허용 (민감값 없음) |

### 코드 사용

```ts
const BASE_URL = import.meta.env.VITE_API_BASE_URL;
```

---

## 백엔드 변경

### CORS — SecurityConfig 인라인

`SecurityConfig.java`에 `@Value`로 `allowed-origins`를 주입하고 `http.cors()` 람다 내부에서 직접 구성.

- `allowCredentials` 미사용 — JWT Bearer 헤더 방식이므로 불필요
- 허용 메서드: `GET, POST, PUT, DELETE, PATCH, OPTIONS`
- 허용 헤더: `*`

### yml 설정

**`application-local.yml`**
```yaml
app:
  cors:
    allowed-origins:
      - http://localhost:5173
```

**`application-prod.yml`**
```yaml
app:
  cors:
    allowed-origins:
      - https://api.sportsify.com
```

> `app.oauth2.redirect-uri`가 이미 `application-local.yml`에 존재하므로 `app:` 블록에 병합한다.

---

## 결정 사항

- Vite 프록시(`server.proxy`) 미사용 — prod 동작과 로컬 동작을 동일하게 유지
- `CorsConfig` 별도 클래스 미생성 — `SecurityConfig` 한 곳에서 관리
- `allowCredentials(true)` 미적용 — JWT 토큰 기반 인증이므로 불필요
