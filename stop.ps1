#Requires -Version 5.1
param(
    [switch]$Down
)
$ErrorActionPreference = "Stop"

$ROOT        = $PSScriptRoot
$COMPOSE_DIR = "$ROOT\kapua-1.6.12\kapua-1.6.12\deployment\docker\compose"
$PROJECT     = "compose"

function Write-Step { param($msg) Write-Host "`n>>> $msg" -ForegroundColor Cyan }
function Write-OK   { param($msg) Write-Host "    [OK] $msg" -ForegroundColor Green }
function Write-Fail { param($msg) Write-Host "    [XX] $msg" -ForegroundColor Red; exit 1 }

# ---- Docker check ----------------------------------------------------------
try {
    docker info 2>&1 | Out-Null
} catch {
    Write-Host "[!!] Docker 未啟動，沒有需要關閉的容器" -ForegroundColor Yellow
    exit 0
}

Set-Location $COMPOSE_DIR

# ---- 關閉 ------------------------------------------------------------------
if ($Down) {
    Write-Step "完整移除容器 (docker compose down)..."
    Write-Host "    注意：下次啟動需重新部署自訂 JAR" -ForegroundColor Yellow
    docker compose -p $PROJECT down
} else {
    Write-Step "暫停容器 (docker compose stop)..."
    docker compose -p $PROJECT stop
}

if ($LASTEXITCODE -ne 0) { Write-Fail "關閉失敗，請用 docker ps 確認狀態" }
Write-OK "所有容器已關閉"

# ---- 確認結果 ---------------------------------------------------------------
Write-Step "確認狀態"
$stillUp = docker ps --format "{{.Names}}" 2>$null |
    Select-String -Pattern "kapua|nginx|broker|db|es|events|job|debug" -Quiet
if ($stillUp) {
    Write-Host "    仍有容器在運行中：" -ForegroundColor Yellow
    docker ps --format "table {{.Names}}`t{{.Status}}" 2>$null |
        Select-String -Pattern "NAMES|kapua|nginx|broker|db|es|events|job|debug"
} else {
    Write-Host "    所有 Kapua 容器已停止" -ForegroundColor Green
}

Write-Host ""
if ($Down) {
    Write-Host "  下次啟動請執行 .\start.ps1（將重新建立容器並自動部署自訂 JAR）" -ForegroundColor DarkGray
} else {
    Write-Host "  下次啟動請執行 .\start.ps1（容器保留，啟動速度較快）" -ForegroundColor DarkGray
}
Write-Host ""