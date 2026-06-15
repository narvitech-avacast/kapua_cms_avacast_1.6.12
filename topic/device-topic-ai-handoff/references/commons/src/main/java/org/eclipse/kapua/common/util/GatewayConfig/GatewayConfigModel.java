package org.eclipse.kapua.common.util.GatewayConfig;

public class GatewayConfigModel {
    
    public static String header="GatewayConfig";
    
    /**
     * 設備名稱（DeviceName）：網關設備的名稱或標識符，用於在Kapua平台中標識和識別該網關設備。
     * 賬戶名稱（AccountName）：與網關設備關聯的賬戶名稱。這可以是網關所屬組織或實體的名稱。
     * Broker協議（BrokerProtocol）：網關與Kapua服務器之間通信所使用的消息代理協議，如MQTT或AMQP。
     * Broker用戶（BrokerUser）：用於在網關設備和Kapua服務器之間進行身份驗證的用戶名。
     * Broker密碼（BrokerPassword）：用於在網關設備和Kapua服務器之間進行身份驗證的密碼或憑證。
     * Broker主機（BrokerHost）：Kapua服務器的主機名或IP地址，網關設備將連接到該主機以與服務器進行通信。
     * Broker端口（BrokerPort）：Kapua服務器的通信端口，網關設備將連接到該端口以與服務器建立連接。
     */
    private String deviceName;
    private String accountName;
    private String brokerProtocol;
    private String brokerUser;
    private String brokerPassword;
    private String brokerHost;
    private String brokerPort;
    
    public String getDeviceName() {
        return deviceName;
    }
    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }
    public String getAccountName() {
        return accountName;
    }
    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }
    public String getBrokerProtocol() {
        return brokerProtocol;
    }
    public void setBrokerProtocol(String brokerProtocol) {
        this.brokerProtocol = brokerProtocol;
    }
    public String getBrokerUser() {
        return brokerUser;
    }
    public void setBrokerUser(String brokerUser) {
        this.brokerUser = brokerUser;
    }
    public String getBrokerPassword() {
        return brokerPassword;
    }
    public void setBrokerPassword(String brokerPassword) {
        this.brokerPassword = brokerPassword;
    }
    public String getBrokerHost() {
        return brokerHost;
    }
    public void setBrokerHost(String brokerHost) {
        this.brokerHost = brokerHost;
    }
    public String getBrokerPort() {
        return brokerPort;
    }
    public void setBrokerPort(String brokerPort) {
        this.brokerPort = brokerPort;
    }


}
