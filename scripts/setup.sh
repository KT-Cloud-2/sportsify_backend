#!/usr/bin/env bash
# 클론 후 한 번만 실행하면 됩니다
# bash scripts/setup.sh

set -e

echo "Git hooks 설정 중..."
git config core.hooksPath .githooks
echo "  → .githooks 폴더를 hook 경로로 설정했습니다."

if [ ! -f ".env.local" ]; then
  cp .env.example .env.local
  echo "  → .env.local 파일을 .env.example 에서 생성했습니다. 값을 채워주세요."
else
  echo "  → .env.local 이미 존재합니다. 건너뜁니다."
fi

echo ""
echo "셋업 완료."
echo ""
echo "다음 단계:"
echo "  1. .env.local 의 환경변수 값을 채우세요."
echo "  2. 인프라 실행: docker compose -f docker-compose.local.yml up -d"
echo "     (PostgreSQL 18 + Redis + Prometheus + Grafana)"
echo "  3. IDE에서 Spring Boot 실행 (SPRING_PROFILES_ACTIVE=local)"
echo "     → 앱 시작 시 Flyway가 DB 스키마를 자동 생성합니다."
echo ""
echo "  환경별 .env 파일:"
echo "    로컬:   .env.local  (SPRING_PROFILES_ACTIVE=local)"
echo "    개발:   .env.dev    (SPRING_PROFILES_ACTIVE=dev)"
echo "    운영:   .env.prod   (SPRING_PROFILES_ACTIVE=prod)"
echo ""
echo "  접속 주소:"
echo "    앱:         http://localhost:8080  (IDE 실행 후)"
echo "    API Docs:   http://localhost:8080/docs.html  (Redocly)"
echo "    Prometheus: http://localhost:9090"
echo "    Grafana:    http://localhost:3001  (admin / admin)"
