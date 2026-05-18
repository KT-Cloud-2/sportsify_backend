# DB Migration Convention

## 파일 네이밍 규칙

```
V{number}__{domain}-schema.sql
```

| 요소 | 설명 | 예시 |
|------|------|------|
| `V{number}` | Flyway 버전 번호 (단조 증가) | `V2`, `V3` |
| `__` | Flyway 구분자 (언더스코어 2개) | |
| `{domain}-schema` | 변경 대상 도메인 + `schema` 고정 접미사 | `notification-schema` |

### 예시

```
V1__init_schema.sql           ← 초기 전체 스키마 (기존)
V2__notification-schema.sql   ← 알림 도메인 컬럼/인덱스 추가
V3__payment-schema.sql        ← 결제 도메인 변경
```

## 원칙

- **V1은 수정하지 않는다.** 이미 배포된 버전이므로 Flyway 체크섬 충돌 발생.
- 스키마 변경은 항상 새 버전 파일로 추가한다.
- 한 파일에는 한 도메인의 변경만 담는다.
- `DROP`, `TRUNCATE` 등 비가역적 DDL은 팀 리뷰 후 적용한다.

## 테스트 컨테이너

- 이미지: `postgres:16-alpine` (`withReuse(true)`)
- V1 이후 새 마이그레이션 파일이 추가되면 컨테이너를 재사용하므로 자동 적용됨.
- V1을 수정한 경우에만 로컬 컨테이너를 수동 삭제해야 한다:
  ```bash
  docker ps -a | grep postgres | awk '{print $1}' | xargs docker rm -f
  ```
