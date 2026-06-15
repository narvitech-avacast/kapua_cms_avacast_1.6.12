# Source Map

## Topic 產生

`references/simulator-kura/.../topic/Topic.java`

- `$EDC` 控制前綴
- `account-name`、`client-id`、`application-id` placeholder
- application、device、reply、notify、data Topic

`references/simulator-kura/.../AbstractMqttTransport.java`

- 將 GatewayConfiguration 的 AccountName 和 ClientId 放入 Topic context

`references/simulator-kura/.../app/ApplicationController.java`

- 裝置實際訂閱 `Topic.application(id).append(wildcard)`
- 展開後為 `$EDC/{AccountName}/{ClientId}/{ApplicationId}/#`

## MQTT 訂閱

`references/simulator-kura/.../MqttAsyncTransport.java`

- 使用 `topic.render(topicContext)` 產生完整 Topic
- 呼叫 Paho `client.subscribe(...)`

`references/client/gateway/.../PahoChannel.java`

- Kapua gateway client 的 Paho subscribe 實作
- subscribe 使用 QoS 1

## GatewayConfig

`references/rest-api/.../Devices.java`

- `deviceAndGatewayConfig`
- `getAndroidGatewayConfigByAccessToken`
- 設定 ClientId、AccountName 和 Broker 連線資料

`references/rest-api/.../model/GatewayConfigXmlGen.java`

- API response 實際欄位
- 現況沒有 SubscribeTopic

`references/commons/.../GatewayConfigModel.java`

- GatewayConfig model

## Broker ACL

`references/broker-core/.../authentication/UserAuthenticationLogic.java`

- 依 AccountName 和 MQTT connect ClientId 建立允許範圍
- 裝置自己的控制 ACL 為 `$EDC.{AccountName}.{ClientId}.>`

`references/broker-core/.../KapuaSecurityBrokerFilter.java`

- subscribe 時檢查 read ACL
- 未授權時丟出 SecurityException

`references/qa/.../BrokerACLDeviceManageI9n.feature`

- Broker ACL 行為範例與預期結果
- 文件中的 `.`、`>` 是 Broker 內部語法；Paho 使用 `/`、`#`

## 設定

`references/commons/src/main/resources/topic-settings.properties`

- `commons.control_message.classifier=$EDC`
- Broker scheme、host、port
- 內容已去除部署密碼，不應從舊專案複製憑證或密碼到新專案
