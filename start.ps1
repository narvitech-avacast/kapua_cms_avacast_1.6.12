#Requires -Version 5.1
param()
$ErrorActionPreference = "Stop"

$ROOT          = $PSScriptRoot
$COMPOSE_DIR   = "$ROOT\kapua-1.6.12\kapua-1.6.12\deployment\docker\compose"
$CUSTOM_JAR    = "$ROOT\kapua-api-resources-running.jar"
$CONTAINER_JAR = "/var/opt/jetty/webapps/root/WEB-INF/lib/kapua-rest-api-resources-1.6.12.jar"
$IMAGE_VER     = "1.6.12"
$PROJECT       = "compose"

function Write-Step { param($msg) Write-Host "`n>>> $msg" -ForegroundColor Cyan }
function Write-OK   { param($msg) Write-Host "    [OK] $msg" -ForegroundColor Green }
function Write-Warn { param($msg) Write-Host "    [!!] $msg" -ForegroundColor Yellow }
function Write-Fail { param($msg) Write-Host "    [XX] $msg" -ForegroundColor Red; exit 1 }

# ---- 1. Docker check -------------------------------------------------------
Write-Step "檢查 Docker Desktop 狀態..."
try {
    docker info 2>&1 | Out-Null
} catch {
    Write-Fail "Docker 未啟動，請先開啟 Docker Desktop 後再執行此腳本"
}
if ($LASTEXITCODE -ne 0) { Write-Fail "Docker 未啟動，請先開啟 Docker Desktop 後再執行此腳本" }
Write-OK "Docker 正常運作"

# ---- 2. 偵測容器狀態 -------------------------------------------------------
Write-Step "偵測容器狀態..."
$existing = docker ps -a --filter "name=^kapua-api$" --format "{{.Names}}" 2>$null
$running  = docker ps   --filter "name=^kapua-api$" --format "{{.Names}}" 2>$null
$needDeploy = $false

Set-Location $COMPOSE_DIR
$env:IMAGE_VERSION = $IMAGE_VER

if ($running -eq "kapua-api") {
    Write-Warn "容器已在運行中，無需重複啟動"
} elseif ($existing -eq "kapua-api") {
    Write-OK "找到已停止的容器，執行 docker compose start..."
    docker compose -p $PROJECT start
    if ($LASTEXITCODE -ne 0) { Write-Fail "docker compose start 失敗" }
} else {
    Write-OK "容器不存在，執行 docker compose up -d（全新啟動）..."
    docker compose -p $PROJECT up -d
    if ($LASTEXITCODE -ne 0) { Write-Fail "docker compose up 失敗" }
    $needDeploy = $true
}

# ---- 3. 部署自訂 JAR（全新啟動才需要）-------------------------------------
if ($needDeploy) {
    Write-Step "等待 kapua-api 容器就緒..."
    $waited = 0
    do {
        Start-Sleep -Seconds 5
        $waited += 5
        $state = docker inspect kapua-api --format "{{.State.Running}}" 2>$null
        Write-Host "    已等待 ${waited}s..." -ForegroundColor DarkGray
    } while ($state -ne "true" -and $waited -lt 60)

    if ($state -ne "true") { Write-Fail "kapua-api 啟動逾時，請用 docker logs kapua-api 查看問題" }

    Write-Step "部署自訂 API JAR..."
    if (-not (Test-Path $CUSTOM_JAR)) {
        Write-Warn "找不到 $CUSTOM_JAR，跳過 JAR 部署（API 將使用原廠版本）"
    } else {
        docker cp "$CUSTOM_JAR" "kapua-api:$CONTAINER_JAR"
        if ($LASTEXITCODE -ne 0) { Write-Fail "docker cp 失敗" }
        Write-OK "JAR 已複製，重啟 kapua-api..."
        docker restart kapua-api
        if ($LASTEXITCODE -ne 0) { Write-Fail "docker restart 失敗" }
        Start-Sleep -Seconds 5
        Write-OK "kapua-api 重啟完成"
    }
}

# ---- 4. 最終狀態 -----------------------------------------------------------
Write-Step "容器狀態"
docker ps --format "table {{.Names}}`t{{.Status}}" 2>$null |
    Select-String -Pattern "NAMES|kapua|nginx|broker|db|es|events|job|debug"

Write-Host ""
Write-Host "============================================" -ForegroundColor DarkGray
Write-Host "  管理介面 : https://cms.test" -ForegroundColor White
Write-Host "  管理介面 : http://localhost:8083" -ForegroundColor White
Write-Host "  REST API : https://cms.test/api/v1" -ForegroundColor White
Write-Host "  偵錯代理 : http://localhost:9999" -ForegroundColor White
Write-Host "============================================" -ForegroundColor DarkGray
Write-Host ""