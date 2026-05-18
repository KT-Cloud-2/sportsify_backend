# 로컬 Docker 운영 가이드

맥북 성능 보호를 위해 로컬 개발 환경은 최소한의 컨테이너만 기동한다.

## 파일 구성

| 파일 | 용도 | 포함 서비스 |
|------|------|-------------|
| `docker-compose.local.yml` | 로컬 개발 (기본) | PostgreSQL, Redis |
| `docker-compose.monitoring.yml` | 로컬 모니터링 (선택) | Prometheus, Grafana |
| `docker-compose.infra.yml` | EC2 인프라 서버 전용 | PostgreSQL, Redis, Prometheus, Grafana |
| `docker-compose.prod.yml` | EC2 앱 서버 전용 | Spring Boot App |

## 빠른 시작

```bash
# 1. 환경 변수 파일 복사
cp .env.example .env.local

# 2. 개발용 DB + Redis 기동 (이것만 쓰는 게 기본)
docker compose -f docker-compose.local.yml --env-file .env.local up -d

# 3. 상태 확인
docker compose -f docker-compose.local.yml ps
```

## 모니터링이 필요할 때만

```bash
# 기동
docker compose -f docker-compose.monitoring.yml up -d

# Prometheus: http://localhost:9090
# Grafana:    http://localhost:3001  (admin / admin)

# 작업 끝나면 반드시 종료
docker compose -f docker-compose.monitoring.yml down
```

## 리소스 제한 (로컬 기준)

| 컨테이너 | CPU 최대 | 메모리 최대 |
|----------|----------|-------------|
| postgres | 1.0 core | 512 MB |
| redis | 0.5 core | 128 MB |
| prometheus | 0.5 core | 256 MB |
| grafana | 0.5 core | 256 MB |

## 자주 쓰는 명령

```bash
# 전체 중지 (볼륨 유지)
docker compose -f docker-compose.local.yml down

# 전체 중지 + 볼륨 삭제 (DB 초기화)
docker compose -f docker-compose.local.yml down -v

# 로그 확인
docker compose -f docker-compose.local.yml logs -f postgres
docker compose -f docker-compose.local.yml logs -f redis

# 컨테이너 리소스 실시간 확인
docker stats --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}"
```

## Docker 기본 명령어

### 컨테이너

```bash
# 실행 중인 컨테이너 목록
docker ps

# 모든 컨테이너 목록 (중지 포함)
docker ps -a

# 특정 컨테이너 시작 / 중지 / 재시작
docker start <컨테이너명>
docker stop <컨테이너명>
docker restart <컨테이너명>

# 실행 중인 모든 컨테이너 중지
docker stop $(docker ps -q)

# 중지된 컨테이너 전체 삭제
docker rm $(docker ps -aq)

# 실행 중인 컨테이너에 셸 접속
docker exec -it <컨테이너명> bash
docker exec -it <컨테이너명> sh   # bash 없는 alpine 이미지

# 컨테이너 로그
docker logs <컨테이너명>
docker logs -f <컨테이너명>        # 실시간 스트리밍
docker logs --tail 100 <컨테이너명> # 최근 100줄
```

### 이미지

```bash
# 로컬 이미지 목록
docker images

# 이미지 삭제
docker rmi <이미지명>

# 사용하지 않는 이미지 전체 삭제
docker image prune -a
```

### 볼륨

```bash
# 볼륨 목록
docker volume ls

# 특정 볼륨 삭제
docker volume rm <볼륨명>

# 사용하지 않는 볼륨 전체 삭제
docker volume prune
```

### 전체 정리 (맥북 용량/성능 확보)

```bash
# 컨테이너 + 이미지 + 볼륨 + 네트워크 한 번에 정리
# ⚠️ 데이터도 삭제되므로 주의
docker system prune -a --volumes

# 사용 중인 디스크 확인
docker system df
```

### docker compose 핵심

```bash
# 기동 (백그라운드)
docker compose -f <파일명> up -d

# 중지 (볼륨 유지)
docker compose -f <파일명> down

# 중지 + 볼륨 삭제
docker compose -f <파일명> down -v

# 컨테이너 상태 확인
docker compose -f <파일명> ps

# 특정 서비스만 재시작
docker compose -f <파일명> restart <서비스명>

# 이미지 재빌드 후 기동
docker compose -f <파일명> up -d --build
```

## 주의사항

- `.env.local` 파일은 절대 커밋하지 않는다 (`.gitignore` 처리됨)
- 모니터링 컨테이너는 작업 후 항상 `down` 처리한다
- postgres 볼륨 마운트 경로는 `/var/lib/postgresql/data` 이어야 한다 (`/var/lib/postgresql` 로 끝나면 데이터 유실 위험)
