# Device MQTT Topic AI Handoff

這份資料包用來協助新專案的 AI 理解 CMS/Kapua 裝置 MQTT Topic、訂閱流程與 Broker ACL。

## 先讀結論

裝置控制 Topic 的基礎格式：

```text
$EDC/{AccountName}/{ClientId}/{ApplicationId}/...
```

裝置通常應訂閱自己的控制範圍：

```text
$EDC/{AccountName}/{ClientId}/#
```

例如：

```text
$EDC/acme/device-001/#
```

特定應用程式可以縮小為：

```text
$EDC/acme/device-001/CONF-V1/#
$EDC/acme/device-001/CMD-V1/#
$EDC/acme/device-001/DEPLOY-V2/#
```

最重要的限制：

1. Topic 第一段必須是 `$EDC`，不是一般資料 Topic。
2. `AccountName` 必須是 CMS 回傳的 account name。
3. Topic 中的 `ClientId` 必須和 MQTT connect 使用的 Client ID 完全相同，包含大小寫。
4. MQTT Topic 分隔符是 `/`。Broker 內部 ACL 顯示的 `.` 與 `>` 是 ActiveMQ 表示法，不可直接拿給 Paho 訂閱。
5. 裝置帳號通常只能訂閱自己的 `$EDC/{AccountName}/{ClientId}/...`。
6. `BrokerUser` 不是 `AccountName`，不可混用。

## GatewayConfig 現況

CMS 的 GatewayConfig API 目前只回傳：

```text
ClientId
AccountName
BrokerProtocol
BrokerUser
BrokerPassword
BrokerHost
BrokerPort
```

它沒有回傳 `SubscribeTopic`。

新專案必須使用 `AccountName` 和 `ClientId` 自行組合：

```text
$EDC/{AccountName}/{ClientId}/#
```

不要使用以下錯誤組合：

```text
$EDC/{BrokerUser}/{ClientId}/#
$EDC/{AccountName}/{BrokerUser}/#
{AccountName}/{ClientId}/#
$EDC/{AccountName}/#
$EDC/#
```

後兩種過寬訂閱通常會被 Broker ACL 拒絕。

## Topic 類型

### 裝置接收 CMS 控制命令

訂閱：

```text
$EDC/{AccountName}/{ClientId}/#
```

CMS 發出的命令可能位於：

```text
$EDC/{AccountName}/{ClientId}/{ApplicationId}/{Method}/{Resource...}
```

例如：

```text
$EDC/acme/device-001/CONF-V1/GET/configurations
$EDC/acme/device-001/CMD-V1/EXEC/command
$EDC/acme/device-001/DEPLOY-V2/GET/packages
```

### 裝置回覆 CMS

回覆格式：

```text
$EDC/{AccountName}/{RequesterClientId}/{ApplicationId}/REPLY/{RequestId}
```

例如：

```text
$EDC/acme/KapuaPool-client-id/CONF-V1/REPLY/req-123
```

注意：回覆 Topic 的 Client ID 可能是請求端的 `RequesterClientId`，不一定是裝置自己的 Client ID。

### 裝置生命週期

裝置會發布：

```text
$EDC/{AccountName}/{ClientId}/MQTT/BIRTH
$EDC/{AccountName}/{ClientId}/MQTT/DC
```

### 一般資料 Topic

一般資料 Topic 不使用 `$EDC`：

```text
{AccountName}/{ClientId}/{DataTopic...}
```

例如：

```text
acme/device-001/telemetry/temperature
```

控制 Topic 與資料 Topic 不要混在一起。

## 建議的新專案流程

1. 呼叫 GatewayConfig API。
2. 驗證 `AccountName`、`ClientId`、Broker 連線欄位皆非空白。
3. MQTT connect 使用 GatewayConfig 的 `ClientId`。
4. connect 成功後才執行 subscribe。
5. 訂閱 `$EDC/{AccountName}/{ClientId}/#`。
6. 記錄實際 Broker URI、MQTT Client ID、BrokerUser 與完整 Topic。
7. 訂閱失敗時保留完整 exception、reason code 與 Broker log。

## 最小診斷資訊

請讓新專案輸出以下資訊，密碼不可輸出：

```text
Broker URI: tcp://host:1883
Broker user: ...
Account name: ...
MQTT connect client ID: ...
Subscribe topic: $EDC/.../.../#
Connected before subscribe: true/false
MQTT reason code: ...
Exception class: ...
Exception message: ...
```

## 常見 Error 原因

### Broker 連線成功，但 subscribe 失敗

優先檢查：

1. Topic 的 AccountName 不正確。
2. Topic 的 ClientId 與 MQTT connect Client ID 不一致。
3. 使用 BrokerUser 代替 AccountName。
4. 訂閱 `$EDC/#` 或 `$EDC/{AccountName}/#`，超出裝置 ACL。
5. connect 尚未完成就 subscribe。
6. Topic 含空白、空段、重複 `/` 或錯誤大小寫。
7. 使用 `.` 或 `>` 這類 ActiveMQ ACL 語法呼叫 Paho。

### 收不到訊息，但 subscribe 沒有報錯

優先檢查：

1. CMS 發布的 AccountName、ClientId、ApplicationId 是否一致。
2. 新專案是否在斷線重連後重新訂閱。
3. 是否訂閱錯誤 ApplicationId。
4. 訊息是否發布在一般資料 Topic，而裝置只訂閱 `$EDC`。
5. callback 是否被替換、釋放或發生未記錄的解析錯誤。

## Broker ACL 的意思

Broker 會依登入帳號所屬 AccountName 和 MQTT connect Client ID 建立 ACL。

一般裝置至少可操作自己的：

```text
$EDC/{AccountName}/{ClientId}/...
{AccountName}/{ClientId}/...
```

Broker 收到訂閱要求時會取得 read ACL。若使用者不在允許的 ACL 中，會丟出：

```text
SecurityException: User ... is not authorized to read from ...
```

Paho 端可能只看到泛用的 `MqttException`，因此 Broker log 非常重要。

## 資料包內容

```text
README.md
AI_PROMPT.md
TopicBuilder.java
references/
```

`references` 內是 CMS 現有關鍵原始碼，供 AI 比對，不建議整份直接搬進新專案。

