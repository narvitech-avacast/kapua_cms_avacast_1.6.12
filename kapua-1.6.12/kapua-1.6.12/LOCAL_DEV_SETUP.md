# Kapua 本地開發設置（無 Docker）

## 前置要求
- Java 8+ ✓
- Maven 3.6+ ✓
- Git ✓

## 選項 1：使用內嵌數據庫和服務（推薦用於開發）

### 步驟 1：構建項目
```bash
cd kapua-1.6.12
mvn clean install -DskipTests -Pdev -pl assembly/api -am
```

### 步驟 2：運行 API 服務（REST API）

從 assembly/api 目錄運行：
```bash
cd assembly/api
mvn jetty:run -Dcommons.db.connection.host=localhost -Dcommons.db.connection.port=5432
```

或使用內嵌 H2 數據庫：
```bash
mvn jetty:run
```

**API 訪問地址**: http://localhost:8080/api

### 步驟 3：運行 Console（可選 - 需要額外配置）

```bash
cd console/web
mvn org.eclipse.jetty:jetty-maven-plugin:9.4.12.v20180830:run-exploded -nsu -Pdev
```

**Console 訪問地址**: http://localhost:8080

**默認登錄憑證**:
- 用戶名: `kapua-sys`
- 密碼: `kapua-password`

## 選項 2：使用外部 MySQL 數據庫

### 步驟 1：安裝 MySQL
```bash
# Windows Chocolatey
choco install mysql

# 或手動下載: https://dev.mysql.com/downloads/mysql/
```

### 步驟 2：創建 Kapua 數據庫
```sql
CREATE DATABASE kapuadb CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'kapua'@'localhost' IDENTIFIED BY 'kapuapassword';
GRANT ALL PRIVILEGES ON kapuadb.* TO 'kapua'@'localhost';
FLUSH PRIVILEGES;
```

### 步驟 3：運行 API 服務
```bash
mvn clean install -DskipTests -Dcommons.db.connection.host=localhost \
  -Dcommons.db.connection.port=3306 \
  -Dcommons.db.connection.username=kapua \
  -Dcommons.db.connection.password=kapuapassword \
  -Dcommons.db.schema=kapuadb
```

## 故障排除

### 問題：無法連接到數據庫
**解決方案**：
- 確保已安裝數據庫服務
- 檢查連接參數（主機、端口、用戶名、密碼）
- 查看 logs 中的詳細錯誤信息

### 問題：內存不足
**解決方案**：增加 Maven JVM 內存
```bash
set MAVEN_OPTS=-Xmx2048m -XX:+UseParallelGC
```

### 問題：端口已被使用
**解決方案**：
```bash
# 更改 Jetty 端口
mvn jetty:run -Djetty.port=8090
```

## 關鍵文件和配置

- **Main POM**: `./pom.xml`
- **API Assembly**: `./assembly/api/pom.xml`
- **Console**: `./console/web/pom.xml`
- **Database Config**: 通過 `-Dcommons.db.*` 屬性配置

## 常用命令

| 命令 | 說明 |
|------|------|
| `mvn clean install -DskipTests` | 構建整個項目 |
| `mvn clean install -DskipTests -pl :kapua-rest-api-web -am` | 只構建 API |
| `mvn clean install -DskipTests -pl :kapua-console-web -am` | 只構建 Console |
