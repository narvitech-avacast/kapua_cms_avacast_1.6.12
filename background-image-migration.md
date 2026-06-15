# Background Image / Signage 圖片播放功能移植清單

這套功能實際名稱是 Digital Signage。完整流程如下：

```text
Signage 頁籤
  -> signage-panel.html 選擇及預覽圖片
  -> POST digitalsignage/media 上傳圖片
  -> POST digitalsignage/create_playlist 儲存播放清單
  -> SIGNAGE-V1 / EXEC / signageplaylist 經 MQTT 傳送
  -> 裝置使用 URL 或 legacy FileRepo API 下載圖片
```

## 一、必須搬移的原始碼

### Console UI

1. `kapua-console-patch/admin/signage-panel.html`
   - 選圖、預覽、排序、移除。
   - 上傳圖片。
   - 建立、更新、刪除及重新播放播放清單。
2. `kapua-1.6.12/kapua-1.6.12/console/module/device/src/main/java/org/eclipse/kapua/app/console/module/device/client/device/signage/DeviceTabSignage.java`
   - 在裝置頁籤中載入 `signage-panel.html`。
3. `kapua-1.6.12/kapua-1.6.12/console/module/device/src/main/java/org/eclipse/kapua/app/console/module/device/client/device/signage/DeviceTabSignageDescriptor.java`
   - 註冊 Signage 裝置頁籤。
4. 在新專案的 `console/web/.../view-descriptors.json` 裝置 tabs 加入：

```json
"org.eclipse.kapua.app.console.module.device.client.device.signage.DeviceTabSignageDescriptor"
```

`IconSet.PICTURE_O` 是 Kapua 既有圖示，不需要額外搬移。

### REST API

1. `DeviceManagementDigitalSignage.java`
   - 圖片上傳與格式/大小驗證。
   - 播放清單 CRUD 與資料表建立。
   - URL 正規化。
   - 透過 `DeviceRequestManagementService` 傳送 `SIGNAGE-V1` MQTT 訊息。
2. `SignageMediaLocation.java`
   - 儲存路徑、公開 URL、路徑安全檢查。
3. `SignagePlaylistResourceNormalizer.java`
   - 將圖片資源轉為裝置可下載的絕對 HTTPS URL。
4. `SignageFileRepos.java`
   - 舊版裝置使用的 `/file-repo/{id}/get-image` 圖片下載 API。
5. `LegacySignagePlaybackCompatibility.java`
   - 舊版播放器只有一張圖片時的相容處理。

這些類別都位於：

```text
kapua-1.6.12/kapua-1.6.12/rest-api/resources/src/main/java/
org/eclipse/kapua/app/api/resources/v1/resources/
```

## 二、建議一併搬移的測試

```text
SignageMediaLocationTest.java
SignagePlaylistResourceNormalizerTest.java
LegacySignagePlaybackCompatibilityTest.java
```

測試位於：

```text
kapua-1.6.12/kapua-1.6.12/rest-api/resources/src/test/java/
org/eclipse/kapua/app/api/resources/v1/resources/
```

## 三、API 契約

基底路徑：

```text
/v1/{scopeId}/devices/{deviceId}/digitalsignage
```

主要端點：

```text
GET    /_new_playlist_id
POST   /media?playlistId={playlistId}
POST   /_query
POST   /create_playlist?timeout=5000
PUT    /update_playlist/{playlistId}?timeout=5000
POST   /play_playlist/{playlistId}?timeout=5000
DELETE /delete_playlist/{playlistId}?timeout=5000
POST   /_wakeup
```

圖片上傳 body 是原始 binary，並使用下列 header：

```text
Content-Type: image/jpeg | image/png | image/gif | image/webp | image/bmp
X-File-Name: original-file-name.png
Authorization: Bearer {token}
```

限制：

```text
單張最大 20 MB
副檔名：jpg、png、gif、webp、bmp
```

MQTT 裝置契約：

```text
App ID: SIGNAGE-V1
Method: EXEC
Resource: signageplaylist
Metric: ds.playlist
requestType: setup_playList | update_playList | delete_playList
```

## 四、部署設定

API container 必須設定：

```yaml
environment:
  - SIGNAGE_MEDIA_STORAGE_ROOT=/var/lib/kapua-signage
  - SIGNAGE_MEDIA_PUBLIC_BASE_URL=https://cms.example.com
volumes:
  - ./file-repo-data:/var/lib/kapua-signage
```

Nginx 必須公開圖片目錄：

```nginx
http {
    include /etc/nginx/mime.types;
    client_max_body_size 20M;

    server {
        location /signage-media/ {
            alias /var/lib/kapua-signage/;
            try_files $uri =404;
            autoindex off;
            limit_except GET HEAD { deny all; }
            add_header X-Content-Type-Options "nosniff" always;
        }
    }
}
```

Nginx container 也要以唯讀方式掛載相同目錄：

```yaml
volumes:
  - ./file-repo-data:/var/lib/kapua-signage:ro
```

舊版 `SIGNAGE-V1` Android 裝置會固定使用 port 8080：

```text
http://{host}:8080/api/v1/{scopeId}/file-repo/{resourceId}/get-image
```

若仍需支援，請移植 `nginx/nginx.conf` 中 port 8080 的 server block，並開放主機 TCP 8080。新裝置若直接使用 `resources[].url`，則不需要此相容入口。

## 五、資料與相依條件

`DeviceManagementDigitalSignage` 會自行建立資料表：

```text
signage_playlist_custom
```

新專案必須已提供下列 Kapua 服務：

```text
DeviceRequestManagementService
GenericRequestFactory
AuthorizationService
PermissionFactory
DeviceCommandManagementService
DeviceCommandFactory
JDBC / javax.json / JAX-RS
```

若新專案不是 Kapua，需將 `DeviceManagementDigitalSignage` 拆成：

```text
MediaStorageService
PlaylistRepository
DeviceSignagePublisher
REST Controller
```

其中 MQTT publisher 必須自行實作上述 `SIGNAGE-V1` 契約。

## 六、不要直接搬移

以下是編譯或部署產物，不是來源：

```text
kapua-console-patch/admin/*.cache.html
kapua-console-patch/admin/*.gwt.rpc
kapua-console-patch/**/*.jar
kapua-api-patch 中已建置的 JAR
file-repo-data 中的測試圖片
```

不要整份複製目前的 `docker-compose.override.yml`，其中包含本機絕對路徑、IP、MQTT 帳號等環境專用設定。只合併本文件列出的 Signage environment、volume 與 port。

## 七、快速匯出

在專案根目錄執行：

```powershell
.\export-background-image.ps1
```

來源會被整理到：

```text
background-image-migration-bundle/
```

