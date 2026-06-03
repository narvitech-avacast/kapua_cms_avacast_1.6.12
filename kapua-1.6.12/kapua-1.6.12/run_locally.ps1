# Windows PowerShell 啟動腳本

# 設置 Maven 選項
$env:MAVEN_OPTS = "-Xmx2048m -XX:+UseParallelGC"

Write-Host "════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "   Kapua 本地開發環境啟動" -ForegroundColor Green
Write-Host "════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""

# 檢查 Java
Write-Host "[1/3] 檢查 Java..." -ForegroundColor Yellow
java -version 2>&1 | Select-Object -First 1
if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Java 未找到！請先安裝 JDK 8+" -ForegroundColor Red
    exit 1
}
Write-Host "✓ Java OK" -ForegroundColor Green
Write-Host ""

# 檢查 Maven
Write-Host "[2/3] 檢查 Maven..." -ForegroundColor Yellow
mvn -version 2>&1 | Select-Object -First 1
if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Maven 未找到！" -ForegroundColor Red
    exit 1
}
Write-Host "✓ Maven OK" -ForegroundColor Green
Write-Host ""

Write-Host "════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "   方案選擇" -ForegroundColor Green  
Write-Host "════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""
Write-Host "A) 使用 Docker Compose（推薦）" -ForegroundColor Yellow
Write-Host "   cd deployment\docker\win" -ForegroundColor Gray
Write-Host "   docker-compose up" -ForegroundColor Gray
Write-Host ""
Write-Host "B) 本地開發（需要 Elasticsearch）" -ForegroundColor Yellow
Write-Host "   1. 先安裝 Elasticsearch："  -ForegroundColor Gray
Write-Host "      choco install elasticsearch" -ForegroundColor Gray
Write-Host "      elasticsearch" -ForegroundColor Gray
Write-Host ""
Write-Host "   2. 在新終端啟動 REST API：" -ForegroundColor Gray
Write-Host "      cd rest-api" -ForegroundColor Gray
Write-Host "      mvn jetty:run -Djetty.port=8081" -ForegroundColor Gray
Write-Host ""
Write-Host "   3. 在新終端啟動 Console（可選）：" -ForegroundColor Gray
Write-Host "      cd console\web" -ForegroundColor Gray
Write-Host "      mvn jetty:run -Djetty.port=8080" -ForegroundColor Gray
Write-Host ""

Write-Host "════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "   訪問地址" -ForegroundColor Green
Write-Host "════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""
Write-Host "Web Console:    http://localhost:8080" -ForegroundColor Cyan
Write-Host "REST API:       http://localhost:8081/api" -ForegroundColor Cyan
Write-Host ""
Write-Host "用戶名: kapua-sys" -ForegroundColor Cyan
Write-Host "密碼:   kapua-password" -ForegroundColor Cyan
Write-Host ""
