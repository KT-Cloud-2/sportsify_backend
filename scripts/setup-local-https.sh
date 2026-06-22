#!/usr/bin/env bash
# 로컬 HTTPS + HTTP/2 자동 설정
# 실행: ./scripts/setup-local-https.sh

set -euo pipefail

CERT_DIR="$(cd "$(dirname "$0")/.." && pwd)/src/main/resources/certs"
KEYSTORE_PATH="$CERT_DIR/local-keystore.p12"
KEYSTORE_PASS="local-https"

echo "=== 1. mkcert 설치 확인 ==="
if ! command -v mkcert &>/dev/null; then
  echo "mkcert 설치 중..."
  brew install mkcert
fi
echo "mkcert $(mkcert -version)"

echo ""
echo "=== 2. 로컬 CA 설치 (sudo 필요) ==="
mkcert -install   # 키체인 등록 → sudo 패스워드 입력 필요

echo ""
echo "=== 3. 인증서 디렉토리 생성 ==="
mkdir -p "$CERT_DIR"

echo ""
echo "=== 4. localhost 인증서 생성 ==="
cd "$CERT_DIR"
mkcert -p12-file local-keystore.p12 -pkcs12 localhost 127.0.0.1 ::1

echo ""
echo "=== 5. keystore 비밀번호 변경 (changeit → local-https) ==="
keytool -importkeystore \
  -srckeystore "$KEYSTORE_PATH" \
  -srcstoretype PKCS12 \
  -srcstorepass changeit \
  -destkeystore "${KEYSTORE_PATH}.tmp" \
  -deststoretype PKCS12 \
  -deststorepass "$KEYSTORE_PASS" \
  -noprompt 2>/dev/null \
  && mv "${KEYSTORE_PATH}.tmp" "$KEYSTORE_PATH" \
  || echo "비밀번호 변경 불필요 (이미 변경됨)"

echo ""
echo "=== 완료 ==="
echo "keystore : $KEYSTORE_PATH"
echo "password : $KEYSTORE_PASS"
echo ""
echo "앱 재시작 후 접근: https://localhost:8443"
echo "k6 실행 시:        BASE_URL=https://localhost:8443 k6 run ..."
