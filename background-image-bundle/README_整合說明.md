# Background Image 功能整合包

## 概述

此整合包包含 CMS 後端 **Background Image（背景圖片）** 功能的所有相關程式碼，
基於 Eclipse Kapua 框架的 `file-repo` 服務模組，可整合進任何相同架構的 Kapua-based 專案。

---

## 資料夾結構

```
background-image-bundle/
├── README_整合說明.md          ← 本說明文件
├── java/
│   ├── api/                    ← 服務介面層（放入 file-repo-api 模組）
│   │   └── org/eclipse/kapua/service/file/repo/
│   │       ├── FileRepo.java              Entity 介面
│   │       ├── FileRepoCreator.java       建立 Entity 用的 Creator 介面
│   │       ├── FileRepoDomain.java        權限 Domain
│   │       ├── FileRepoDomains.java       Domain 常數
│   │       ├── FileRepoFactory.java       Factory 介面
│   │       ├── FileRepoListResult.java    查詢結果介面
│   │       ├── FileRepoQuery.java         查詢介面
│   │       ├── FileRepoService.java       服務介面（含所有業務方法）
│   │       └── FileRepoXmlRegistry.java   JAXB XML 工廠
│   │
│   ├── internal/               ← 服務實作層（放入 file-repo-internal 模組）
│   │   └── org/eclipse/kapua/service/file/repo/internal/
│   │       ├── FileRepoCreatorImpl.java        Creator 實作
│   │       ├── FileRepoDAO.java                資料存取物件
│   │       ├── FileRepoEntityManagerFactory.java  JPA EntityManager Factory
│   │       ├── FileRepoFactoryImpl.java        Factory 實作
│   │       ├── FileRepoImpl.java               JPA Entity 實作（對應 file_repo 資料表）
│   │       ├── FileRepoListResultImpl.java     查詢結果實作
│   │       ├── FileRepoQueryImpl.java          查詢實作
│   │       └── FileRepoServiceImpl.java        服務核心實作（上傳/壓縮/刪除/分段上傳）
│   │
│   ├── job/                    ← 排程清理任務（選用，需要 Quartz）
│   │   └── org/eclipse/kapua/service/file/repo/job/
│   │       └── CleanFileJob.java          定時清理過期圖片的 Quartz Job
│   │
│   └── rest-api/               ← REST API 端點（放入 rest-api 模組）
│       └── FileRepos.java      完整的 REST Resource（上傳/下載/查詢/刪除）
│
├── db/                         ← 資料庫 migration 腳本（Liquibase）
│   ├── changelog-master.xml    主 changelog（依序引入各版本）
│   ├── 0.1.0/
│   │   ├── changelog-file_repo-0.1.0.xml
│   │   ├── file_repo.xml       建立 file_repo 資料表
│   │   └── file_repo_ttl.xml   新增 ttl 欄位
│   ├── 0.2.0/
│   │   ├── changelog-file_repo-0.2.0.xml
│   │   └── filerepo_domain.xml  新增權限 Domain 資料
│   └── 0.3.0/
│       └── changelog-file_repo-0.3.0.xml
│
└── persistence/
    └── persistence.xml         JPA persistence unit 設定（persistence-unit name="file-repo"）
```

---

## 資料庫 Schema（file_repo 資料表）

```sql
CREATE TABLE file_repo (
    scope_id          BIGINT(21) UNSIGNED,
    id                BIGINT(21) UNSIGNED NOT NULL PRIMARY KEY,
    name              VARCHAR(255) NOT NULL,       -- 原始檔名（含中文）
    created_on        TIMESTAMP(3) DEFAULT NOW(),
    created_by        BIGINT(21) UNSIGNED NOT NULL,
    modified_on       TIMESTAMP(3) NOT NULL DEFAULT NOW(),
    modified_by       BIGINT(21) UNSIGNED NOT NULL,
    attributes        TEXT,
    properties        TEXT,
    optlock           INT UNSIGNED,
    image_path        TEXT,           -- 圖片在伺服器上的完整路徑
    dir               TEXT,           -- 上傳目錄
    thumbnail         TEXT,           -- 縮圖路徑
    thumbnail_ratio   TEXT,           -- 縮圖尺寸，格式: "300*150"
    ttl               DATETIME        -- 過期時間，NULL 或空白 = 永久保留
);
```

---

## REST API 端點說明

基礎路徑：`/{scopeId}/file-repo`

| 方法     | 路徑                              | 說明                          |
|----------|-----------------------------------|-------------------------------|
| `POST`   | `/{scopeId}/file-repo`            | 上傳圖片（multipart/form-data）|
| `GET`    | `/{scopeId}/file-repo/chunk-start`| 取得分段上傳 UUID             |
| `POST`   | `/{scopeId}/file-repo/chunk`      | 分段上傳（大檔案）            |
| `POST`   | `/{scopeId}/file-repo/account`    | 帶權限驗證的圖片上傳          |
| `GET`    | `/{scopeId}/file-repo/{id}`       | 取得 FileRepo metadata        |
| `GET`    | `/{scopeId}/file-repo/{id}/get-image`     | 下載原始圖片 binary  |
| `GET`    | `/{scopeId}/file-repo/{id}/get-thumbnail` | 下載縮圖              |
| `PUT`    | `/{scopeId}/file-repo/{id}/activate`      | 設為永久（清除 TTL）  |
| `POST`   | `/{scopeId}/file-repo/query`      | 查詢圖片列表                  |
| `POST`   | `/{scopeId}/file-repo/count`      | 計數                          |
| `DELETE` | `/{scopeId}/file-repo/{id}`       | 刪除圖片（含實體檔案）        |
| `DELETE` | `/{scopeId}/file-repo/{id}/account`| 帶權限驗證的刪除             |
| `GET`    | `/{scopeId}/file-repo/clean-timeout`| 手動觸發清理過期圖片        |

### 上傳請求範例（multipart）

```
POST /{scopeId}/file-repo
Content-Type: multipart/form-data

file      = <image binary>
ttl       = "2026-12-31 23:59:59"  (可選，不填則永久保留)
```

---

## 整合步驟

### Step 1 — 複製 Java 原始碼

將以下檔案放入新專案對應的 Maven 模組：

| 來源資料夾              | 目標 Maven 模組              |
|-------------------------|------------------------------|
| `java/api/`             | `file-repo-api` 模組的 `src/main/java/` |
| `java/internal/`        | `file-repo-internal` 模組的 `src/main/java/` |
| `java/job/`             | `file-repo-job` 模組（或合併進 internal）|
| `java/rest-api/FileRepos.java` | `rest-api/resources` 模組 |

### Step 2 — 設定 persistence.xml

將 `persistence/persistence.xml` 中的 `<persistence-unit name="file-repo">` 區塊
**合併**進新專案的 persistence.xml，或直接放入 `file-repo-internal/src/main/resources/META-INF/`。

關鍵內容：
```xml
<persistence-unit name="file-repo" transaction-type="RESOURCE_LOCAL">
    <class>org.eclipse.kapua.service.file.repo.internal.FileRepoImpl</class>
    <!-- ... 其他 commons class ... -->
</persistence-unit>
```

### Step 3 — 執行資料庫 Migration

將 `db/` 下所有 Liquibase XML 複製到新專案的 Liquibase 資料夾，
然後在主 changelog 中加入引用：

```xml
<!-- 在你的 master changelog 加入這行 -->
<include file="path/to/changelog-file_repo-master.xml"/>
```

執行 Liquibase update 後，資料庫會自動建立 `file_repo` 資料表並注入權限 Domain 資料。

### Step 4 — 設定系統參數

`FileRepoServiceImpl` 和 `FileRepos.java` 依賴兩個 SystemSetting key，
確認新專案的 `system.properties`（或對應設定檔）有以下設定：

```properties
# 部署模式：vm 或 docker
build.type=docker

# docker 模式下的檔案儲存根目錄
docker.filerepo.storage=/data
# 上傳目錄會是：/data/upload/
# 縮圖目錄會是：/data/upload/thumbnail/
# 分段暫存目錄：/data/upload/chunk/
```

對應的 SystemSettingKey enum 需要包含：
```java
BUILD_TYPE("build.type"),
DOCKER_FILEREPO_STORAGE("docker.filerepo.storage")
```

### Step 5 — 註冊 REST Resource（JAX-RS）

在新專案的 JAX-RS Application 類別加入 `FileRepos`：

```java
// 在 Application.getSingletons() 或 getClasses() 中加入
classes.add(FileRepos.class);
```

或透過 web.xml / 掃描 package 自動掃描到。

### Step 6 — 設定 KapuaLocator（選用）

`FileRepoFactoryImpl` 和 `FileRepoServiceImpl` 透過 `@KapuaProvider` 自動注冊，
若新專案使用相同的 Locator 機制，啟動時會自動掃描到。
若使用 Spring 或其他 DI，則需要手動 bean 注冊。

### Step 7 — 排程清理過期圖片（選用）

`CleanFileJob` 使用 Quartz scheduler，在 Quartz 設定中加入：

```java
JobDetail job = JobBuilder.newJob(CleanFileJob.class)
    .withIdentity("cleanFileJob")
    .build();

Trigger trigger = TriggerBuilder.newTrigger()
    .withSchedule(CronScheduleBuilder.cronSchedule("0 0 3 * * ?")) // 每天凌晨 3 點
    .build();

scheduler.scheduleJob(job, trigger);
```

或透過 REST API 手動觸發：`GET /{scopeId}/file-repo/clean-timeout`

---

## 主要業務邏輯說明

### 圖片上傳流程（FileRepos.java + FileRepoServiceImpl.java）

1. 接收 multipart 請求，讀取 `InputStream`
2. 用 UUID 產生隨機不重複檔名
3. `writeToFile()` — 寫入磁碟
4. 若副檔名為 `.jpg/.jpeg/.png`，呼叫 `zipImageFile()` 壓縮並生成縮圖（寬 300px 等比例縮放）
5. 讀取縮圖尺寸存入 `thumbnailRatio`（格式 `寬*高`）
6. 呼叫 `FileRepoDAO.create()` 寫入 `file_repo` 資料表

### 圖片壓縮邏輯（FileRepoServiceImpl.java）

```java
// 等比例壓縮（設定寬，高自動計算）
zipImageFile(oldFile, newFile, 300, 0, 0.6f);

// 固定寬高壓縮
zipWidthHeightImageFile(oldFile, newFile, 800, 600, 0.8f);
```

### 分段上傳流程（大檔案）

1. `GET /chunk-start` — 取得唯一 UUID
2. 多次 `POST /chunk` — 分批上傳各段（附帶 chunkNumber、totalChunks）
3. 最後一段上傳完成後自動合併、建立縮圖、寫入資料庫

### TTL 過期機制

- 上傳時可附帶 `ttl` 參數（格式：`yyyy-MM-dd HH:mm:ss`）
- `PUT /{id}/activate` 可清除 TTL，使圖片永久保留
- `cleanTtlFile()` 掃描所有已過期記錄，刪除磁碟檔案和資料庫記錄

---

## 依賴套件（pom.xml）

新專案需要以下 Maven 依賴：

```xml
<!-- JAX-RS / Jersey -->
<dependency>
    <groupId>org.glassfish.jersey.media</groupId>
    <artifactId>jersey-media-multipart</artifactId>
</dependency>

<!-- Java Image IO（標準 JDK 內建，無須額外引入）-->

<!-- Quartz（若使用 CleanFileJob） -->
<dependency>
    <groupId>org.quartz-scheduler</groupId>
    <artifactId>quartz</artifactId>
    <version>2.3.2</version>
</dependency>

<!-- Kapua Commons（同專案應已有） -->
<dependency>
    <groupId>org.eclipse.kapua</groupId>
    <artifactId>kapua-commons</artifactId>
</dependency>
```

---

## 注意事項

1. **檔案路徑**：`FileRepoImpl` 的 `image_path`、`thumbnail` 欄位儲存的是伺服器上的**絕對路徑**，
   搬移環境時如果儲存路徑不同，舊資料的路徑需要更新。

2. **中文檔名**：上傳時使用 ISO-8859-1 → UTF-8 轉換處理中文檔名：
   ```java
   byte[] fileNameBytes = rawFileName.getBytes(StandardCharsets.ISO_8859_1);
   String decodedFileName = new String(fileNameBytes, StandardCharsets.UTF_8);
   ```

3. **權限控制**：`create()` 方法（無 `_account` 後綴）沒有做權限驗證，
   `create_account()`、`delete_account()` 才有。依照新專案需求決定是否加入驗證。

4. **persistence-unit name**：固定為 `"file-repo"`，
   `FileRepoEntityManagerFactory` 中的 `PERSISTENCE_UNIT_NAME` 必須與 persistence.xml 一致。
