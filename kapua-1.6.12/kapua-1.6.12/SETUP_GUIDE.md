# Kapua 運行指南

## 環境檢查 ✓
- **Java**: OpenJDK 1.8.0_492 ✓
- **Maven**: 3.9.12 ✓

---

## 最簡單的方式：使用 Docker Compose

### 步驟 1：導航到 Docker 目錄
```powershell
cd deployment\docker\win
```

### 步驟 2：啟動所有服務
```powershell
# 使用提供的腳本
./docker-deploy.ps1

# 或直接用 docker-compose
docker-compose up -d
```

### 步驟 3：訪問應用
- **Web Console**: http://localhost:8080
- **REST API**: http://localhost:8081/api

### 步驟 4：登錄
```
用戶名: kapua-sys
密碼: kapua-password
```

### 停止服務
```powershell
cd deployment\docker\win
docker-compose down
```

---

## 備選方式：本地開發運行

如果你真的需要本地運行（不用 Docker），需要以下步驟：

### 前置條件

#### 1. 安裝 Elasticsearch 7.8.x

```powershell
# 使用 Chocolatey
choco install elasticsearch

# 或手動下載
# https://www.elastic.co/downloads/past-releases/elasticsearch-7-8-1

# 啟動 Elasticsearch
elasticsearch

# 驗證（新終端）
curl http://localhost:9200
```

#### 2. 安裝 MySQL（可選，用於生產）

```powershell
choco install mysql

# 或使用 H2（內嵌，開發用）
```

### 本地構建步驟

#### 步驟 1：嘗試構建（可能會失敗）

```powershell
cd d:\Kapua_cms_bill_1.6.12\kapua-1.6.12\kapua-1.6.12

# 如果上面的 clean install 有問題，試試這個
mvn install -DskipTests -T 1C

# 或只構建必要的模塊
mvn install -DskipTests -pl rest-api/core,rest-api/web,console/web -am
```

#### 步驟 2：運行 REST API

在終端中打開新標籤頁或新窗口：

```powershell
cd rest-api
mvn jetty:run -Djetty.port=8081
```

✓ 訪問: http://localhost:8081/api

#### 步驟 3：運行 Console（新終端）

```powershell
cd console\web
mvn org.eclipse.jetty:jetty-maven-plugin:9.4.12.v20180830:run-exploded ^
  -nsu -Pdev -DuseTestScope=true ^
  -Djetty.daemon=false ^
  -Dcommons.db.connection.host=localhost
```

✓ 訪問: http://localhost:8080

---

## 系統架構

```
┌────────────────────────────────────┐
│  Kapua Web Console (GWT)           │  Port 8080
│  http://localhost:8080/admin       │
└─────────────┬──────────────────────┘
              │
┌─────────────▼──────────────────────┐
│  REST API (Jersey)                 │  Port 8081
│  http://localhost:8081/api         │
└─────────────┬──────────────────────┘
              │
      ┌───────┴──────────┬──────────┬──────────┐
      │                  │          │          │
   ┌──▼──┐        ┌──────▼──┐  ┌───▼──┐  ┌────▼────┐
   │ H2  │        │Elast    │  │MQTT  │  │ActiveMQ│
   │  DB │        │search   │  │Broker│  │ (JMS)  │
   └─────┘        └─────────┘  └──────┘  └────────┘
```

---

## 常見問題

### Q: 為什麼我應該用 Docker？
A: 
- ✓ 自動配置所有依賴（MySQL, Elasticsearch, MQTT 等）
- ✓ 開箱即用，5 分鐘啟動
- ✓ 與生產環境一致
- ✓ 便於升級和管理

### Q: 本地開發的優點是什麼？
A:
- ✓ 更好地控制每個組件
- ✓ 可以單獨調試 REST API 或 Console
- ✓ 編輯代碼後自動重新加載（使用 Jetty 開發模式）

### Q: 端口被佔用了怎麼辦？
A:
```powershell
# 改用其他端口
mvn jetty:run -Djetty.port=9090
```

### Q: 默認登錄信息在哪裡？
A:
```
用戶名: kapua-sys
密碼: kapua-password
```

### Q: 如何查看日誌？
A: Jetty 和應用日誌會直接輸出到控制台。搜索 `ERROR` 或 `WARN` 以找到問題。

---

## 推薦的開發工作流

1. **使用 Docker 運行完整系統**（測試和演示）
   ```powershell
   cd deployment\docker\win
   docker-compose up -d
   ```

2. **使用本地開發模式調試代碼**
   ```powershell
   # 修改代碼後，Jetty 會自動重新加載
   cd rest-api
   mvn jetty:run
   ```

3. **編輯代碼 → 保存 → 自動重新加載（Jetty DevTools）**

---

## 後續步驟

- 查看 [QUICK_START.md](QUICK_START.md) 瞭解更多詳情
- 查看 [docs/developer-guide](docs/developer-guide) 瞭解開發文檔
- 查看 [deployment/docker/unix/docker-compose.yml](deployment/docker/unix/docker-compose.yml) 瞭解完整的服務配置

