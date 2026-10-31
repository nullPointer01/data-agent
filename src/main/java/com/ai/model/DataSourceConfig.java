package com.ai.model;

import java.util.Date;
import java.util.UUID;

/**
 * 外部数据源配置。
 *
 * @author data-agent
 */
public class DataSourceConfig {

    private String datasourceId;

    private String name;

    private String type;

    private String host;

    private Integer port;

    private String dbName;

    private String username;

    private String password;

    private String description;

    private boolean enabled = true;

    private String tenantId;

    private String userId;

    private Date createdAt;

    private Date updatedAt;

    public void onCreate() {
        if (datasourceId == null) {
            datasourceId = UUID.randomUUID().toString();
        }
        createdAt = new Date();
        updatedAt = new Date();
    }

    public void onUpdate() {
        updatedAt = new Date();
    }

    public String getDatasourceId() {
        return datasourceId;
    }

    public void setDatasourceId(String datasourceId) {
        this.datasourceId = datasourceId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getDbName() {
        return dbName;
    }

    public void setDbName(String dbName) {
        this.dbName = dbName;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }
}
