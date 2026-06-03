###############################################################################
# Kapua 混合開發模式啟動腳本
#
# 架構：
#   Docker  => 基礎設施 (DB:3307, Elasticsearch:9200, Events Broker:5672, MQTT Broker:1883/1893)
#   本地    => 應用程式 (REST API:8081, Console:8080) 透過 Maven Jetty 執行
#
# 優點：
#   - 改完 Java 程式碼只需 mvn compile，不需重建 Docker image
#   - JDWP debug port 直接掛到 IDE 除錯
#   - DB 用 3307 避免與本地 MySQL(3306) 衝突
#
# 用法：
#   .\dev-hybrid.ps1                             # 啟動基礎設施並顯示本地啟動指令
#   .\dev-hybrid.ps1 -restDebugPort 5005         # 自訂 REST API debug port
#   .\dev-hybrid.ps1 -consoleDebugPort 5006      # 自訂 Console debug port
#   .\dev-hybrid.ps1 -suspend                    # 啟動時暫停等待 debugger 連線
#   .\dev-hybrid.ps1 -dbPort 3307                # 自訂 H2 DB host port
#   .\dev-hybrid.ps1 -stop                       # 停止所有容器
###############################################################################
#Requires -Version 7

Param(
    [switch]$stop             = $false,
    [string]$restDebugPort    = "5005",
    [string]$consoleDebugPort = "5006",
    [string]$dbPort           = "3307",
    [switch]$suspend          = $false
)

$script_dir  = Split-Path (Get-Variable MyInvocation).Value.MyCommand.Path
$compose_dir = Join-Path $script_dir ".." "compose"

$infra_compose = Join-Path $compose_dir "docker-compose.hybrid-infra.yml"

# ── Stop 模式 ────────────────────────────────────────────────────────────────
if ($stop) {
    Write-Host "停止基礎設施容器..." -ForegroundColor Yellow
    $env:KAPUA_DB_PORT = $dbPort
    docker compose -f $infra_compose down
    Write-Host "已停止。" -ForegroundColor Green
    exit 0
}

Write-Host ""
Write-Host "╔══════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║   Kapua 混合開發模式 (Hybrid Dev + Debug)        ║" -ForegroundColor Cyan
Write-Host "╚══════════════════════════════════════════════════╝" -ForegroundColor Cyan
Write-Host ""

# ── 環境檢查 ─────────────────────────────────────────────────────────────────
Write-Host "[1/3] 檢查 Java..." -ForegroundColor Yellow
$javaCmd = Get-Command java -ErrorAction SilentlyContinue
if (-not $javaCmd) {
    Write-Host "  ✗ Java 未找到！請安裝 JDK 8+" -ForegroundColor Red
    exit 1
}
$javaVer = (java -version 2>&1)[0]
Write-Host "  ✓ $javaVer" -ForegroundColor Green

Write-Host "[2/3] 檢查 Maven..." -ForegroundColor Yellow
$mvnCmd = Get-Command mvn -ErrorAction SilentlyContinue
if (-not $mvnCmd) {
    Write-Host "  ✗ Maven 未找到！請安裝 Maven 3.6+" -ForegroundColor Red
    exit 1
}
$mvnVer = (mvn -version 2>&1)[0]
Write-Host "  ✓ $mvnVer" -ForegroundColor Green

Write-Host "[3/3] 檢查 Docker..." -ForegroundColor Yellow
$dockerCmd = Get-Command docker -ErrorAction SilentlyContinue
if (-not $dockerCmd) {
    Write-Host "  ✗ Docker 未找到！請安裝 Docker Desktop" -ForegroundColor Red
    exit 1
}
docker info 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host "  ✗ Docker 未運行！請先啟動 Docker Desktop" -ForegroundColor Red
    exit 1
}
Write-Host "  ✓ Docker 已就緒" -ForegroundColor Green
Write-Host ""

# ── 設定環境變數 ─────────────────────────────────────────────────────────────
$env:KAPUA_DB_PORT = $dbPort
if (-not $env:CRYPTO_SECRET_KEY) { $env:CRYPTO_SECRET_KEY = "dockerSecretKey!" }

# ── 啟動 Docker 基礎設施 ─────────────────────────────────────────────────────
Write-Host "啟動基礎設施容器 (DB:$dbPort / ES:9200 / Broker:1883,1893 / Events:5672)..." -ForegroundColor Yellow
Write-Host ""

docker compose -f $infra_compose up -d

if ($LASTEXITCODE -ne 0) {
    Write-Host "✗ 基礎設施啟動失敗！" -ForegroundColor Red
    exit 1
}
Write-Host ""
Write-Host "✓ 基礎設施已啟動！" -ForegroundColor Green
Write-Host ""

# ── 等待服務就緒 ─────────────────────────────────────────────────────────────
Write-Host "等待服務就緒 (15s)..." -ForegroundColor Yellow
Start-Sleep -Seconds 15

# ── 計算參數 ─────────────────────────────────────────────────────────────────
$suspendVal  = if ($suspend) { "y" } else { "n" }
$projectRoot = Resolve-Path (Join-Path $script_dir ".." ".." "..")

# ── 顯示本地啟動指令 ─────────────────────────────────────────────────────────
Write-Host "╔══════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║   本地服務啟動指令（請在新終端視窗執行）         ║" -ForegroundColor Cyan
Write-Host "╚══════════════════════════════════════════════════╝" -ForegroundColor Cyan
Write-Host ""

Write-Host "▶ 【REST API】Port 8081 │ Debug Port $restDebugPort" -ForegroundColor Yellow
Write-Host ""
Write-Host ('  $env:MAVEN_OPTS = "-Xmx2048m -agentlib:jdwp=transport=dt_socket,server=y,suspend=' + $suspendVal + ',address=' + $restDebugPort + '"') -ForegroundColor White
Write-Host ("  cd `"$projectRoot`"") -ForegroundColor White
Write-Host "  mvn org.eclipse.jetty:jetty-maven-plugin:9.4.53.v20231009:run ``" -ForegroundColor White
Write-Host "    -PlocalDev -Dmaven.test.skip=true ``" -ForegroundColor White
Write-Host "    -Dcommons.db.connection.host=localhost ``" -ForegroundColor White
Write-Host "    -Dcommons.db.connection.port=$dbPort ``" -ForegroundColor White
Write-Host "    -Dcommons.eventbus.url=amqp://localhost:5672 ``" -ForegroundColor White
Write-Host "    -Dbroker.host=localhost ``" -ForegroundColor White
Write-Host "    -Ddatastore.elasticsearch.nodes=localhost:9200 ``" -ForegroundColor White
Write-Host "    -Djetty.port=8081 -Djetty.scanIntervalSeconds=5 ``" -ForegroundColor White
Write-Host "    ""-Dcertificate.jwt.private.key=file:///D:/Kapua_cms_bill_1.6.12/kapua-1.6.12/kapua-1.6.12/qa/integration/src/test/resources/certificates/jwt/test.key"" ``" -ForegroundColor White
Write-Host "    ""-Dcertificate.jwt.certificate=file:///D:/Kapua_cms_bill_1.6.12/kapua-1.6.12/kapua-1.6.12/qa/integration/src/test/resources/certificates/jwt/test.cert"" ``" -ForegroundColor White
Write-Host "    -Dcommons.db.schema.update=true" -ForegroundColor White
Write-Host ""

Write-Host "▶ 【Web Console】Port 8080 │ Debug Port $consoleDebugPort" -ForegroundColor Yellow
Write-Host ""
Write-Host ('  $env:MAVEN_OPTS = "-Xmx2048m -agentlib:jdwp=transport=dt_socket,server=y,suspend=' + $suspendVal + ',address=' + $consoleDebugPort + '"') -ForegroundColor White
Write-Host ("  cd `"$projectRoot`"") -ForegroundColor White
Write-Host "  mvn org.eclipse.jetty:jetty-maven-plugin:9.4.53.v20231009:run-exploded ``" -ForegroundColor White
Write-Host "    -pl console/web -am -nsu -Pdev -DskipTests ``" -ForegroundColor White
Write-Host "    -Dcommons.db.connection.host=localhost ``" -ForegroundColor White
Write-Host "    -Dcommons.db.connection.port=$dbPort ``" -ForegroundColor White
Write-Host "    -Dcommons.eventbus.url=amqp://localhost:5672 ``" -ForegroundColor White
Write-Host "    -Dbroker.host=localhost ``" -ForegroundColor White
Write-Host "    -Ddatastore.elasticsearch.nodes=localhost:9200 ``" -ForegroundColor White
Write-Host "    -Djetty.port=8080" -ForegroundColor White
Write-Host ""

Write-Host "╔══════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║   服務端口總覽                                   ║" -ForegroundColor Cyan
Write-Host "╚══════════════════════════════════════════════════╝" -ForegroundColor Cyan
Write-Host ""
Write-Host "  [本地]  Web Console    http://localhost:8080" -ForegroundColor Green
Write-Host "  [本地]  REST API       http://localhost:8081/api" -ForegroundColor Green
Write-Host "  [本地]  Swagger UI     http://localhost:8081/api/doc" -ForegroundColor Green
Write-Host ""
Write-Host "  [Docker] H2 DB 控制台  http://localhost:8181" -ForegroundColor Cyan
Write-Host "           JDBC URL: jdbc:h2:tcp://localhost:${dbPort}/kapuadb" -ForegroundColor Gray
Write-Host "           用戶名: kapua   密碼: kapua" -ForegroundColor Gray
Write-Host ""
Write-Host "  [Docker] Elasticsearch  http://localhost:9200" -ForegroundColor Cyan
Write-Host "  [Docker] MQTT Broker    localhost:1883 (外部設備), 1893 (內部服務)" -ForegroundColor Cyan
Write-Host "  [Docker] Events Broker  localhost:5672 (AMQP)" -ForegroundColor Cyan
Write-Host ""
Write-Host "╔══════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║   IDE 遠端除錯 (Remote Debug) 設定              ║" -ForegroundColor Cyan
Write-Host "╚══════════════════════════════════════════════════╝" -ForegroundColor Cyan
Write-Host ""
Write-Host "  IntelliJ IDEA / VS Code 新增 Remote JVM Debug 設定：" -ForegroundColor Yellow
Write-Host "    REST API    => Host: localhost, Port: $restDebugPort" -ForegroundColor White
Write-Host "    Web Console => Host: localhost, Port: $consoleDebugPort" -ForegroundColor White
Write-Host ""
Write-Host "  熱重載工作流程：" -ForegroundColor Yellow
Write-Host "    1. 修改 Java 程式碼" -ForegroundColor White
Write-Host "    2. 執行 mvn compile -pl <module> -am 重新編譯" -ForegroundColor White
Write-Host "    3. Jetty 每 5 秒掃描 class 變更並自動重載" -ForegroundColor White
Write-Host "    4. 簡單方法體變更可用 IDE HotSwap (IDEA: Ctrl+Shift+F9)" -ForegroundColor White
Write-Host ""
Write-Host "  預設登入帳號：kapua-sys / kapua-password" -ForegroundColor Yellow
Write-Host ""
Write-Host "  停止基礎設施：.\dev-hybrid.ps1 -stop" -ForegroundColor Red
Write-Host ""
