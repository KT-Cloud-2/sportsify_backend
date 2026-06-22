# k6/notification/run-windows.ps1
# Windows 11 PowerShell에서 Mac 알림 서버 부하 테스트 실행
#
# 사전 조건:
#   1. k6 설치: winget install k6 --source winget
#   2. Mac에서 run-mac-prep.sh <VUS> 실행 (DB seed)
#   3. k6/ 폴더 전체를 Windows PC로 복사 (구조 그대로 유지)
#      예: C:\k6-sortsify\  <-- 이 스크립트는 notification\ 안에 있어야 함
#
# 사용법:
#   .\run-windows.ps1                         # all, 기본 3000 VU
#   .\run-windows.ps1 -Target connect         # 연결 수립 테스트
#   .\run-windows.ps1 -Target send            # 발행 TPS 테스트
#   .\run-windows.ps1 -Target receive         # 수신 전파 테스트
#   .\run-windows.ps1 -Target all -Vus 1000   # VU 수 지정
#   .\run-windows.ps1 -BaseUrl https://192.168.1.8:8443  # IP 직접 지정
#
# 토큰은 k6 setup()이 서버로부터 직접 발급받으므로 파일 복사 불필요

param(
    [string]$Target  = "all",
    [int]   $Vus     = 3000,
    [string]$BaseUrl = ""
)

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$LogDir    = Join-Path $ScriptDir "logs"

if ($BaseUrl -eq "") {
    $BaseUrl = if ($env:BASE_URL) { $env:BASE_URL } else { "https://192.168.1.8:8443" }
}

$SeedOffset = 10000

# ── 사전 확인 ─────────────────────────────────────────────────
if (-not (Get-Command k6 -ErrorAction SilentlyContinue)) {
    Write-Error "k6가 없습니다. 설치: winget install k6 --source winget"
    exit 1
}

Write-Host ""
Write-Host "========================================================"
Write-Host "  k6 알림 부하 테스트 (Windows -> Mac)"
Write-Host "  BASE_URL : $BaseUrl"
Write-Host "  Target   : $Target  /  VU : $Vus"
Write-Host "  토큰은 setup()에서 서버로부터 직접 발급됩니다"
Write-Host "========================================================"
Write-Host ""

if (-not (Test-Path $LogDir)) {
    New-Item -ItemType Directory -Path $LogDir | Out-Null
}

# ── 서버 연결 사전 확인 ───────────────────────────────────────
Write-Host "▶ 서버 연결 확인 중..."
try {
    # TLS 인증서 검증 무시 (자체 서명 인증서)
    [System.Net.ServicePointManager]::ServerCertificateValidationCallback = { $true }
    $response = Invoke-RestMethod -Uri "$BaseUrl/dev/token?memberId=1" -TimeoutSec 10
    Write-Host "✔ 서버 응답 확인 (토큰 발급 정상)"
} catch {
    Write-Error "서버 응답 없음: $BaseUrl - Mac에서 앱이 기동 중인지 확인하세요"
    exit 1
}

# ── k6 실행 함수 ──────────────────────────────────────────────
function Run-K6 {
    param([string]$Script, [string[]]$ExtraArgs = @())

    $ScriptPath = Join-Path $ScriptDir $Script
    $Timestamp  = Get-Date -Format "yyyyMMdd_HHmmss"
    $BaseName   = [System.IO.Path]::GetFileNameWithoutExtension($Script)
    $LogFile    = Join-Path $LogDir "${BaseName}_${Timestamp}.log"

    Write-Host "▶ [k6] $Script 실행 중... (로그: $LogFile)"

    $k6Args = @(
        "run",
        "--insecure-skip-tls-verify",
        "-e", "BASE_URL=$BaseUrl",
        "-e", "K6_SEED_OFFSET=$SeedOffset",
        "-e", "MAX_VUS=$Vus"
    ) + $ExtraArgs + @($ScriptPath)

    & k6 @k6Args 2>&1 | Tee-Object -FilePath $LogFile

    if ($LASTEXITCODE -eq 0) {
        Write-Host "✔ $Script 완료"
    } else {
        Write-Warning "✘ $Script 종료 코드: $LASTEXITCODE (로그: $LogFile)"
    }
    Write-Host ""
}

# ── 시나리오 실행 ──────────────────────────────────────────────
switch ($Target) {
    "connect" { Run-K6 "connect.js" @("--log-output=none") }
    "sustain" { Run-K6 "sustain.js" @("--log-output=none") }
    "send"    { Run-K6 "send.js" }
    "receive" { Run-K6 "receive.js" @("--log-output=none") }
    "all"     { Run-K6 "all.js"     @("--log-output=none") }
    default {
        Write-Error "알 수 없는 Target: $Target"
        Write-Host "사용법: .\run-windows.ps1 -Target [connect|sustain|send|receive|all] [-Vus N]"
        exit 1
    }
}

Write-Host "========================================================"
Write-Host "  완료. Mac에서 cleanup:"
Write-Host "  ./k6/notification/run-mac-prep.sh $Vus --cleanup"
Write-Host "========================================================"
