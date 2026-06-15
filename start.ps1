#Requires -Version 5.1
param()
$ErrorActionPreference = "Stop"

$ROOT        = $PSScriptRoot
$COMPOSE_DIR = "$ROOT\kapua-1.6.12\kapua-1.6.12\deployment\docker\compose"
$PATCH_DIR   = "$ROOT\kapua-api-patch"
$CONSOLE_PATCH_DIR = "$ROOT\kapua-console-patch"
$IMAGE_VER   = "1.6.12"
$PROJECT     = "compose"

function Write-Step { param($msg) Write-Host "`n>>> $msg" -ForegroundColor Cyan }
function Write-OK   { param($msg) Write-Host "    [OK] $msg" -ForegroundColor Green }
function Write-Warn { param($msg) Write-Host "    [!!] $msg" -ForegroundColor Yellow }
function Write-Fail { param($msg) Write-Host "    [XX] $msg" -ForegroundColor Red; exit 1 }

function Test-IsAdministrator {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object Security.Principal.WindowsPrincipal($identity)
    return $principal.IsInRole(
        [Security.Principal.WindowsBuiltInRole]::Administrator
    )
}

# ---- 1. Docker check -------------------------------------------------------
Write-Step "檢查 Docker Desktop 狀態..."
try {
    docker info 2>&1 | Out-Null
} catch {
    Write-Fail "Docker 未啟動，請先開啟 Docker Desktop 後再執行此腳本"
}
if ($LASTEXITCODE -ne 0) { Write-Fail "Docker 未啟動，請先開啟 Docker Desktop 後再執行此腳本" }
Write-OK "Docker 正常運作"

# Legacy SIGNAGE-V1 devices download FileRepo resources from host port 8080.
Write-Step "檢查 Signage FileRepo 防火牆規則..."
$fileRepoRuleName = "Kapua Signage FileRepo 8080"
$fileRepoRule = Get-NetFirewallRule `
    -DisplayName $fileRepoRuleName `
    -ErrorAction SilentlyContinue

if ($fileRepoRule) {
    Write-OK "TCP 8080 入站規則已存在"
} elseif (Test-IsAdministrator) {
    New-NetFirewallRule `
        -DisplayName $fileRepoRuleName `
        -Direction Inbound `
        -Action Allow `
        -Protocol TCP `
        -LocalPort 8080 `
        -Profile Any | Out-Null
    Write-OK "已建立 TCP 8080 入站規則"
} else {
    Write-Warn "缺少 TCP 8080 入站規則；請以系統管理員身分執行 start.ps1"
}

# ---- 2. 同步 patch image（若自訂 JAR 有更新才重建）------------------------
Write-Step "檢查 kapua-api patch image..."
$patchedImg = docker images "kapua/kapua-api:1.6.12-patched" --format "{{.ID}}" 2>$null

# 比較 patch 目錄裡最新修改時間 vs image 建立時間
$patchMtime = (Get-ChildItem $PATCH_DIR | Sort-Object LastWriteTime -Descending | Select-Object -First 1).LastWriteTime
if ($patchedImg) {
    $imgCreated = [datetime](docker inspect "kapua/kapua-api:1.6.12-patched" --format "{{.Created}}" 2>$null)
    if ($patchMtime -gt $imgCreated) {
        Write-Warn "patch 檔案有更新，重新建立 image..."
        $patchedImg = $null
    }
}

if (-not $patchedImg) {
    Write-OK "建立 kapua/kapua-api:1.6.12-patched image..."
    # 同步自訂 JAR 到 patch 目錄
    Copy-Item "$ROOT\kapua-api-resources-running.jar" "$PATCH_DIR\kapua-api-resources-running.jar" -Force
    docker build -t "kapua/kapua-api:1.6.12-patched" $PATCH_DIR
    if ($LASTEXITCODE -ne 0) { Write-Fail "docker build 失敗" }
    Write-OK "image 建立完成"
} else {
    Write-OK "patch image 已是最新版本"
}

# ---- 2b. 同步 Console patch image ------------------------------------------
Write-Step "檢查 kapua-console patch image..."
$consolePatchedImg = docker images "kapua/kapua-console:1.6.12-patched" --format "{{.ID}}" 2>$null
$consolePatchMtime = (Get-ChildItem $CONSOLE_PATCH_DIR | Sort-Object LastWriteTime -Descending | Select-Object -First 1).LastWriteTime
if ($consolePatchedImg) {
    $consoleImgCreated = [datetime](docker inspect "kapua/kapua-console:1.6.12-patched" --format "{{.Created}}" 2>$null)
    if ($consolePatchMtime -gt $consoleImgCreated) {
        Write-Warn "Console patch 檔案有更新，重新建立 image..."
        $consolePatchedImg = $null
    }
}

if (-not $consolePatchedImg) {
    docker build -t "kapua/kapua-console:1.6.12-patched" $CONSOLE_PATCH_DIR
    if ($LASTEXITCODE -ne 0) { Write-Fail "kapua-console docker build 失敗" }
    Write-OK "Console image 建立完成"
} else {
    Write-OK "Console patch image 已是最新版本"
}

# ---- 3. 偵測容器狀態 -------------------------------------------------------
Write-Step "偵測容器狀態..."
$existing = docker ps -a --filter "name=^kapua-api$" --format "{{.Names}}" 2>$null
$running  = docker ps   --filter "name=^kapua-api$" --format "{{.Names}}" 2>$null

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
