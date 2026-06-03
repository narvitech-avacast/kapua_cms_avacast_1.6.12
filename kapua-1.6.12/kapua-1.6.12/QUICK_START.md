# Kapua 快速啟動指南（無 Docker）

## 推薦方案：使用 Docker Compose（最簡單）

如果你最終還是想用 Docker（這是最簡單的方式），執行：

```bash
cd deployment/docker/unix
./docker-deploy.sh
```

訪問: http://localhost:8080

默認憑證：
- 用戶：`kapua-sys`
- 密碼：`kapua-password`

---

## 備選方案：純本地開發環境

如果堅持不使用 Docker，需要以下步驟：

### 1. 安裝前置依賴

#### 安裝 Elasticsearch（用於數據存儲）

**Windows:**
```bash
# 使用 Chocolatey
choco install elasticsearch

# 或手動下載：https://www.elastic.co/downloads/elasticsearch
# 版本要求：7.8.x（與 Kapua 1.6.12 兼容）
```

**啟動 Elasticsearch:**
```bash
elasticsearch
# 訪問：http://localhost:9200
```

#### 安裝 MySQL（可選，用於生產級別）

```bash
choco install mysql
```

### 2. 構建 Kapua 核心模塊

如果完整構建失敗，嘗試構建單個模塊：

```bash
cd d:\Kapua_cms_bill_1.6.12\kapua-1.6.12\kapua-1.6.12

# 重新嘗試，跳過 clean（避免文件鎖定）
mvn install -DskipTests -o 2>/dev/null || mvn install -DskipTests
```

### 3. 運行 REST API（端口 8081）

```bash
cd rest-api
mvn jetty:run -Djetty.port=8081
```

訪問: http://localhost:8081/api

### 4. 運行 Web Console（端口 8080，可選）

```bash
cd console/web
mvn org.eclipse.jetty:jetty-maven-plugin:9.4.12.v20180830:run-exploded \
  -nsu -Pdev -DuseTestScope=true -Djetty.daemon=false \
  -Dcommons.db.connection.host=localhost
```

訪問: http://localhost:8080

---

## 架構說明

Kapua 由以下組件組成：

```
┌─────────────────────────────────────────┐
│   Web Console (GWT)                     │ Port 8080
│   http://localhost:8080/admin           │
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│   REST API (Jersey/JAX-RS)              │ Port 8081
│   http://localhost:8081/api             │
└──────────────┬──────────────────────────┘
               │
├─ SQL Database (H2 / MySQL)              │
├─ Elasticsearch (數據存儲)                │
├─ MQTT Broker (消息服務)                  │
└─ Event Bus (消息隊列)
```

---

## 默認憑證

```
用戶名: kapua-sys
密碼: kapua-password
```

---

## 常見問題

**Q: 為什麼推薦用 Docker？**
A: 因為 Kapua 有複雜的依賴關係（MySQL、Elasticsearch、MQTT Broker 等）。Docker 可以自動配置所有這些。

**Q: 沒有 Docker 的情況下運行是否可行？**
A: 可行，但需要：
- 手動安裝 MySQL、Elasticsearch
- 理解 Java/Maven 構建過程
- 可能需要配置 MQTT Broker（ActiveMQ）

**Q: 端口已被佔用怎麼辦？**
A:
```bash
# 更改 Jetty 端口
mvn jetty:run -Djetty.port=8090
```

**Q: 如何查看日誌？**
A: Jetty 會在控制台輸出日誌，可以搜索 ERROR 或 WARN。

---

## 下一步建議

1. **最簡單**：使用 Docker Compose （`./deployment/docker/unix/docker-deploy.sh`）
2. **如果堅持本地**：先安裝 Elasticsearch，然後運行 REST API
3. **開發模式**：編輯源代碼後，Jetty 會自動重新加載

