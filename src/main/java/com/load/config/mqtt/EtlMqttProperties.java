package com.load.config.mqtt;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "etl.mqtt")
public class EtlMqttProperties {

    private boolean enabled;
    private String brokerUrl;
    private String clientId;
    private String namespace;
    private String serviceName;
    private String schemaVersion;
    private String username;
    private String password;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBrokerUrl() {
        return brokerUrl;
    }

    public void setBrokerUrl(String brokerUrl) {
        this.brokerUrl = brokerUrl;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(String schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String commandStartTopic() {
        return String.format("etl/%s/%s/cmd/start", namespace, serviceName);
    }

    public String eventRunningTopic() {
        return String.format("etl/%s/%s/event/running", namespace, serviceName);
    }

    public String eventCompletedTopic() {
        return String.format("etl/%s/%s/event/completed", namespace, serviceName);
    }

    public String eventFailedTopic() {
        return String.format("etl/%s/%s/event/failed", namespace, serviceName);
    }
}
