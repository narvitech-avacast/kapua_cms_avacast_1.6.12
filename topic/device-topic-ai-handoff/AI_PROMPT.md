# Prompt For The New Project AI

請先完整閱讀同資料夾的 `README.md`、`TopicBuilder.java` 與 `references`。

你的任務是檢查新專案的 MQTT 裝置訂閱流程，並修正裝置收不到 CMS Topic 或 subscribe 持續報錯的問題。

請依下列規則工作：

1. 找出 GatewayConfig response model、MQTT connect、subscribe、reconnect 和 message callback 的實際程式碼。
2. 確認 GatewayConfig 是否只有 `AccountName` 與 `ClientId`，不要假設後端會回傳 `SubscribeTopic`。
3. 控制 Topic 必須組成 `$EDC/{AccountName}/{ClientId}/#`。
4. Topic 中的 ClientId 必須與建立 MQTT connection 時使用的 Client ID 完全相同。
5. 不可把 BrokerUser 當成 AccountName。
6. 不可把 Broker ACL 的 `.`、`>` 語法當成 MQTT 的 `/`、`#`。
7. 必須等 connect 成功後再 subscribe。
8. 必須在 reconnect 後重新 subscribe，除非使用的 client library 已明確保證恢復訂閱。
9. 記錄非敏感診斷資訊與完整 exception；不可記錄密碼。
10. 修正後加入 Topic builder 單元測試，以及 connect/subscribe 流程測試或可執行的驗證方式。

請先輸出你找到的：

```text
GatewayConfig AccountName 來源：
GatewayConfig ClientId 來源：
MQTT connect ClientId：
實際 subscribe Topic：
subscribe 執行時 connection state：
錯誤 exception 與 reason code：
```

接著直接修改新專案，避免只提供推測。

