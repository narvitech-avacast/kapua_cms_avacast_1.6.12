# Digital Signage 裝置下載修正懶人包

> 將本文件完整貼給新專案的 AI，請它先比對實際類別、設定與部署檔案，再依照下列要求修改。  
> 不要直接假設檔名與行號完全相同。

## 一、任務目標

修正 Digital Signage 的完整流程：

```text
前端上傳檔案
  -> Server 儲存檔案
  -> Server 產生裝置可存取的完整 HTTPS URL
  -> Server 透過 MQTT 發送 playlist JSON
  -> Kura SIGNAGE-V1 handler 收到 ds.playlist
  -> 裝置下載檔案
  -> 裝置回覆明確的成功或失敗結果
```

目前可能存在以下問題：

1. playlist 內只有 `/signage-media/...` 相對路徑，裝置不知道 hostname。
2. Nginx 沒有正確公開 `/signage-media/`。
3. API container 與 Nginx container 沒有共用 media volume。
4. 裝置沒有安裝或啟動 `SIGNAGE-V1` handler。
5. MQTT timeout 被一律轉成「裝置未連線」，無法判斷真正原因。
6. marquee endpoint 可能在移植時遺漏，或捕捉例外後仍回 HTTP 200。

## 二、AI 執行要求

請先搜尋並確認：

```text
DeviceManagementDigitalSignage.java
DeviceDigitalSignage.java
SignageServiceImpl.java
SignageRequestPayload.java
TranslatorAppDigitalSignageKapuaKura.java
DigitalSignageMetrics.java
nginx.conf
server.conf
docker-compose.yml / compose.yml
signage-media
ds.playlist
SIGNAGE-V1
signageplaylist
marquee
```

修改時遵守：

- 使用專案現有的 framework、設定注入和 exception mapper。
- 不硬編碼正式環境 domain。
- 不使用 request 的 `Host` header 直接產生公開 URL。
- 不破壞既有 playlist JSON 欄位；若已有 `path`，保留它並新增或填入完整 `url`。
- URL 必須在送 MQTT 前完成。
- DB 與 MQTT 應保存、傳送一致的 playlist。
- 不要把所有 MQTT timeout 都誤判成 handler 未安裝。

## 三、後端修改

### 3.1 增加公開 URL 設定

依專案設定格式加入：

```properties
signage.media.storage-root=/var/lib/kapua-signage
signage.media.public-base-url=https://cms.example.com
```

環境變數建議：

```yaml
environment:
  SIGNAGE_MEDIA_STORAGE_ROOT: /var/lib/kapua-signage
  SIGNAGE_MEDIA_PUBLIC_BASE_URL: https://cms.example.com
```

正式環境的 `public-base-url` 必須是裝置可連線且憑證可信任的 HTTPS 網址。

### 3.2 集中產生 media URL

不要在 controller 中到處用字串相加。建立單一 URL builder，例如：

```java
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class SignageMediaLocation {

    private final Path storageRoot;
    private final URI publicBaseUri;

    public SignageMediaLocation(String storageRoot, String publicBaseUrl) {
        this.storageRoot = Paths.get(storageRoot).toAbsolutePath().normalize();

        URI uri = URI.create(publicBaseUrl);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new IllegalArgumentException(
                    "signage.media.public-base-url must be an absolute HTTPS URL");
        }

        String normalized = publicBaseUrl.endsWith("/")
                ? publicBaseUrl
                : publicBaseUrl + "/";
        this.publicBaseUri = URI.create(normalized);
    }

    public StoredMedia resolve(String scopeId, String folder, String storedFileName) {
        String safeScopeId = safeSegment(scopeId, "scopeId");
        String safeFolder = safeSegment(folder, "folder");
        String safeFileName = safeSegment(storedFileName, "storedFileName");

        String relativePath = safeScopeId + "/" + safeFolder + "/" + safeFileName;
        Path target = storageRoot.resolve(relativePath).normalize();

        if (!target.startsWith(storageRoot)) {
            throw new IllegalArgumentException("Invalid signage media path");
        }

        String publicPath = "/signage-media/" + relativePath.replace('\\', '/');
        URI publicUrl = publicBaseUri.resolve(publicPath.substring(1));
        return new StoredMedia(target, publicPath, publicUrl.toString());
    }

    private static String safeSegment(String value, String field) {
        if (value == null || !value.matches("[A-Za-z0-9._-]+")
                || ".".equals(value) || "..".equals(value)) {
            throw new IllegalArgumentException("Invalid " + field);
        }
        return value;
    }

    public static final class StoredMedia {
        private final Path diskPath;
        private final String publicPath;
        private final String publicUrl;

        public StoredMedia(Path diskPath, String publicPath, String publicUrl) {
            this.diskPath = diskPath;
            this.publicPath = publicPath;
            this.publicUrl = publicUrl;
        }

        public Path getDiskPath() {
            return diskPath;
        }

        public String getPublicPath() {
            return publicPath;
        }

        public String getPublicUrl() {
            return publicUrl;
        }
    }
}
```

若專案已有 URI builder，改用既有工具，不要重複造輪子。

### 3.3 修改上傳 endpoint

原本若是：

```java
String publicPath =
        "/signage-media/" + scopeId + "/" + folder + "/" + storedFileName;
response.setPath(publicPath);
```

修改概念：

```java
SignageMediaLocation.StoredMedia media =
        signageMediaLocation.resolve(
                scopeId.toString(),
                folder,
                storedFileName);

Files.createDirectories(media.getDiskPath().getParent());

try (InputStream input = uploadedFileInputStream) {
    Files.copy(input, media.getDiskPath(), StandardCopyOption.REPLACE_EXISTING);
}

response.setPath(media.getPublicPath()); // 保留給 UI 或相容用途
response.setUrl(media.getPublicUrl());   // 裝置下載必須使用完整 URL
```

如果 response model 沒有 `url`：

```java
private String url;

public String getUrl() {
    return url;
}

public void setUrl(String url) {
    this.url = url;
}
```

同時驗證：

- 檔案大小上限。
- MIME type 白名單。
- 副檔名白名單。
- 不使用使用者原始檔名作為磁碟檔名。
- 使用 UUID 或 server-generated filename。
- 拒絕 `/`、`\`、`..` 與控制字元。

### 3.4 送 MQTT 前正規化 playlist URL

若前端可能仍送相對 `resource.path`，service 必須在保存 DB 與送 MQTT 前補成完整 URL。

推薦在 service 建立：

```java
private void normalizeResourceUrls(SignagePlayListCreator creator) {
    if (creator.getResources() == null) {
        return;
    }

    for (SignageResource resource : creator.getResources()) {
        String path = resource.getPath();
        if (path == null || path.trim().isEmpty()) {
            continue;
        }

        URI uri = URI.create(path);
        if (uri.isAbsolute()) {
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalArgumentException(
                        "Signage resource URL must use HTTPS");
            }
            continue;
        }

        if (!path.startsWith("/signage-media/")) {
            throw new IllegalArgumentException(
                    "Unsupported relative signage resource path: " + path);
        }

        resource.setPath(signageMediaPublicUrlResolver.toAbsoluteUrl(path));
    }
}
```

呼叫時機：

```java
normalizeResourceUrls(creator);

// 此後才序列化、寫 DB、建立 ds.playlist 及送 MQTT。
String creatorJson = gson.toJson(creator);
```

較佳的長期資料模型：

```json
{
  "name": "Image-123",
  "id": "file-id",
  "fileName": "photo.jpg",
  "path": "/signage-media/scope/images/uuid.jpg",
  "url": "https://cms.example.com/signage-media/scope/images/uuid.jpg",
  "sha256": "hex-value",
  "size": 123456,
  "contentType": "image/jpeg"
}
```

但若 Kura handler 目前只讀 `path`，先把 `path` 改成完整 URL，或同步更新 handler 支援 `url` 優先、`path` fallback。

### 3.5 不要用字串 replace 修改 ID JSON

若現有程式包含：

```java
String newJson = creatorJson
        .replace(bigScopeId, compactScopeId)
        .replace(bigId, compactId);
```

應直接修改 Java object 的 ID，或使用 Gson `JsonObject` 精確修改欄位，避免誤改其他內容：

```java
JsonObject json = JsonParser.parseString(creatorJson).getAsJsonObject();
json.addProperty("scopeId", compactScopeId);
json.addProperty("id", compactId);
String normalizedJson = gson.toJson(json);
```

請依專案實際 JSON 結構處理；如果 ID 是物件，不要硬當字串欄位。

## 四、Nginx 修改

在實際對外 HTTPS server block 加入：

```nginx
server {
    listen 443 ssl;
    server_name cms.example.com;

    client_max_body_size 50M;

    location /signage-media/ {
        alias /var/lib/kapua-signage/;
        try_files $uri =404;

        autoindex off;
        add_header X-Content-Type-Options nosniff always;
        add_header Cache-Control "public, max-age=31536000, immutable";
    }

    location /api/ {
        proxy_pass http://kapua-api:8080/;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

注意 `alias` 的尾端 `/` 必須保留。

HTTP 80 建議只轉址：

```nginx
server {
    listen 80;
    server_name cms.example.com;
    return 301 https://$host$request_uri;
}
```

若裝置完全不支援 HTTPS，才在 port 80 額外公開 media；正式環境不建議這樣做。

部署前執行：

```bash
nginx -t
curl -I https://cms.example.com/signage-media/<scope>/<folder>/<file>
curl --fail --output /tmp/signage-test \
  https://cms.example.com/signage-media/<scope>/<folder>/<file>
```

## 五、Docker Compose 修改

API 與 Nginx 必須掛載同一個 named volume：

```yaml
services:
  kapua-api:
    environment:
      SIGNAGE_MEDIA_STORAGE_ROOT: /var/lib/kapua-signage
      SIGNAGE_MEDIA_PUBLIC_BASE_URL: https://cms.example.com
    volumes:
      - signage_media:/var/lib/kapua-signage

  nginx:
    volumes:
      - signage_media:/var/lib/kapua-signage:ro
      - ./nginx/nginx.conf:/etc/nginx/nginx.conf:ro

volumes:
  signage_media:
```

API 使用 read-write；Nginx 使用 read-only。

如果是多主機部署，不可使用各主機本機 volume，應改用：

- S3/MinIO/object storage，或
- NFS/shared persistent volume。

否則 API 寫入主機 A，Nginx 或下一個 API instance 可能在主機 B 找不到檔案。

## 六、MQTT 與 Kura handler

服務端契約必須維持：

```text
App ID: SIGNAGE-V1
Method: EXEC
Resource: signageplaylist
Metric key: ds.playlist
```

裝置端 handler 必須：

1. 註冊 `SIGNAGE-V1`。
2. 處理 `EXEC/signageplaylist`。
3. 讀取 `ds.playlist`。
4. 驗證 JSON schema 與 request type。
5. 下載 HTTPS URL。
6. 檢查 HTTP status、content length 與 checksum。
7. 使用暫存檔下載，成功後再 atomic rename。
8. 回覆具體錯誤，不要只讓 request timeout。

裝置回覆 metrics 建議：

```json
{
  "status": "FAILED",
  "stage": "DOWNLOAD",
  "resourceUrl": "https://cms.example.com/signage-media/...",
  "httpStatus": 404,
  "errorCode": "MEDIA_NOT_FOUND",
  "message": "HTTP 404 while downloading media"
}
```

成功：

```json
{
  "status": "SUCCESS",
  "stage": "APPLY_PLAYLIST",
  "playlistId": "compact-id",
  "downloadedResources": 3
}
```

## 七、Server MQTT 錯誤處理

目前若所有 exception 都轉成 `DEVICE_NOT_CONNECTED`，請拆分至少以下情況：

```text
DEVICE_OFFLINE
CONTROL_TIMEOUT
SIGNAGE_HANDLER_UNAVAILABLE
DEVICE_REJECTED_PLAYLIST
MEDIA_DOWNLOAD_FAILED
INVALID_DEVICE_RESPONSE
```

範例概念：

```java
try {
    response = (SignageResponseMessage) executor.send();
} catch (TimeoutException e) {
    throw new SignageControlTimeoutException(
            "Device did not reply to SIGNAGE-V1/EXEC/signageplaylist", e);
} catch (DeviceNotConnectedException e) {
    throw e;
} catch (Exception e) {
    throw new SignageDeliveryException(
            "Unable to deliver signage playlist", e);
}

if (response == null) {
    throw new SignageDeliveryException("Device returned no response");
}

if (!response.getResponseCode().isAccepted()) {
    throw new SignageDeviceRejectedException(
            extractDeviceError(response));
}
```

保留原始 exception 作為 cause，並記錄：

```text
scopeId
deviceId
playlistId
requestId/correlationId
appId
resource
timeout
```

不要在 log 中輸出 access token 或完整敏感 payload。

## 八、恢復 hello/capability handshake

如果現有 service 已建立 `hello_playList` payload，但 `send()` 被註解，請恢復並驗證：

```java
SignageResponseMessage helloResponse =
        (SignageResponseMessage) helloExecutor.send();

if (helloResponse == null
        || !helloResponse.getResponseCode().isAccepted()) {
    throw new SignageHandlerUnavailableException(
            "SIGNAGE-V1 handler did not accept hello_playList");
}
```

hello response 建議包含：

```json
{
  "status": "READY",
  "handlerVersion": "1.2.0",
  "supportedRequestTypes": [
    "setup_playList",
    "update_playList",
    "delete_playList",
    "setup_marquee"
  ],
  "supportsHttps": true
}
```

hello 成功只能證明 handler 有回覆，不代表檔案一定可下載，因此仍須保留下載結果回報。

## 九、Marquee endpoint

確認存在：

```text
POST /{scopeId}/devices/{deviceId}/digitalsignage/marquee
```

如果缺少，依現有 playlist endpoint 的注入、權限與 exception mapping 模式加入：

```java
@POST
@Path("marquee")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public Response createOrUpdateMarquee(
        @PathParam("scopeId") ScopeId scopeId,
        @PathParam("deviceId") EntityId deviceId,
        @QueryParam("timeout") Long timeout,
        SignagePlayListCreator creator) throws KapuaException {

    signageService.createMarquee(scopeId, deviceId, timeout, creator);
    return Response.ok().build();
}
```

不要這樣寫：

```java
try {
    signageService.createMarquee(...);
} catch (KapuaException e) {
    e.printStackTrace();
}
return Response.ok().build();
```

失敗必須交由 exception mapper 回傳正確 HTTP status。

另外不要使用：

```java
metadata.contains("marquee")
```

判斷資料類型。應使用結構化欄位，例如：

```java
entityProperties.getProperty("list_type").equals("marquee")
```

或 DB 中的獨立欄位。

## 十、資料一致性

目前流程若先建立 DB record，再送 MQTT，MQTT 失敗時可能留下 inactive record。請明確採用其中一種策略：

### 推薦策略

1. 建立 DB record，狀態設為 `PENDING`。
2. 送 MQTT。
3. 裝置成功套用後改成 `ACTIVE`。
4. timeout 改成 `DELIVERY_TIMEOUT`。
5. 裝置拒絕或下載失敗改成 `FAILED`，保存錯誤原因。
6. 支援 retry，使用相同 operation ID 避免重複套用。

不要讓 REST 回 200，但 DB 或裝置其實失敗。

## 十一、安全要求

至少完成：

- 恢復 Signage service 的 `execute/read/write/delete` authorization checks。
- 防止 path traversal。
- Nginx 禁止 directory listing。
- 限制上傳大小、MIME 與副檔名。
- 使用 server-generated filename。
- media URL 僅使用 HTTPS。
- URL 若包含敏感內容，改用短效 signed URL，而不是永久公開 URL。
- 不讓不同 scope 猜測 URL 後讀取其他租戶資源。

若 media 不能公開，推薦改為 object storage presigned URL：

```text
https://object.example.com/bucket/key?signature=...&expires=...
```

有效期限必須大於裝置可能的離線時間；或設計裝置重新取得 URL 的 API。

## 十二、測試要求

請新增或更新以下測試：

### Unit tests

- base URL 正規化。
- relative path 轉 absolute HTTPS URL。
- 拒絕 HTTP URL。
- 拒絕 `..`、slash 與非法 filename。
- playlist 所有 resource 都完成 URL 正規化。
- marquee exception 不會回 200。

### Integration tests

1. 上傳圖片。
2. 確認檔案存在於共享 volume。
3. 使用 upload response 的完整 URL 執行 GET。
4. 確認 HTTP 200、Content-Type 與內容一致。
5. 建立 playlist。
6. 確認 MQTT topic/app/resource 正確。
7. 確認 `ds.playlist` 內為完整 HTTPS URL。
8. 模擬 device success response，DB 狀態成為 ACTIVE。
9. 模擬 timeout，REST 不可回 200。
10. 模擬 device download 404，API/operation 狀態保存明確錯誤。

## 十三、驗收條件

全部符合才算完成：

- [ ] MQTT 的 `ds.playlist` 不再包含無 hostname 的 media 相對 URL。
- [ ] 裝置可直接下載 payload 中的 URL。
- [ ] HTTPS Nginx 正確提供 `/signage-media/`。
- [ ] API 與 Nginx 使用同一份 media storage。
- [ ] Nginx container 對 media volume 僅有讀權限。
- [ ] `SIGNAGE-V1/EXEC/signageplaylist` translator 與 handler 契約一致。
- [ ] timeout、裝置離線、handler 不存在與下載失敗可區分。
- [ ] marquee endpoint 存在，失敗不會回 HTTP 200。
- [ ] DB 能反映 PENDING、ACTIVE、FAILED 或 TIMEOUT。
- [ ] authorization 與 path traversal 防護已完成。
- [ ] unit test 與 integration test 通過。

## 十四、要求 AI 最後回報

修改完成後請提供：

1. 修改檔案清單。
2. 每個檔案的修改摘要。
3. 最終 playlist JSON 範例。
4. 最終 MQTT app/resource/metric。
5. Nginx 與 Docker volume 配置。
6. 執行過的測試與結果。
7. 尚未能驗證的裝置端項目。
8. 部署時必須設定的環境變數。

