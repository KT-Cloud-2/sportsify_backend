#!/usr/bin/env bash
# 사용법: ./scripts/capture-dashboards.sh [출력_디렉토리]
# 기본 출력: docs/captures/YYYY-MM-DD_HH-MM-SS/

set -euo pipefail

GRAFANA_URL="${GRAFANA_URL:-http://localhost:3001}"
GRAFANA_USER="${GRAFANA_USER:-admin}"
GRAFANA_PASSWORD="${GRAFANA_PASSWORD:-admin}"
OUTPUT_DIR="${1:-docs/captures/$(date +%Y-%m-%d_%H-%M-%S)}"
WIDTH=1600
HEIGHT=900
FROM="now-1h"
TO="now"

mkdir -p "$OUTPUT_DIR"

echo "Grafana 대시보드 목록 조회 중..."
DASHBOARDS=$(curl -sf -u "$GRAFANA_USER:$GRAFANA_PASSWORD" \
  "$GRAFANA_URL/api/search?type=dash-db" | \
  python3 -c "
import json, sys
for d in json.load(sys.stdin):
    print(d['uid'] + '|' + d['title'])
")

if [ -z "$DASHBOARDS" ]; then
  echo "ERROR: 대시보드를 찾을 수 없습니다. Grafana가 실행 중인지 확인하세요." >&2
  exit 1
fi

while IFS='|' read -r uid title; do
  filename=$(echo "$title" | tr ' /' '_-' | tr -cd '[:alnum:]_-')
  output="$OUTPUT_DIR/${filename}.png"
  echo "캡처: $title → $output"
  curl -sf -u "$GRAFANA_USER:$GRAFANA_PASSWORD" \
    "$GRAFANA_URL/render/d/$uid/?width=$WIDTH&height=$HEIGHT&from=$FROM&to=$TO&theme=dark" \
    -o "$output" || echo "  WARN: $title 캡처 실패"
done <<< "$DASHBOARDS"

echo "완료: $OUTPUT_DIR"
