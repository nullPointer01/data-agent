package com.ai.model;

import java.util.UUID;

import java.time.LocalDateTime;

/**
 * 租户隔离的可配置 Agent。
 *
 * @author data-agent
 */
public class AgentProfile {

    private String agentId;

    private String name;

    private String description;

    private String systemPrompt;

    private String modelId;

    /** 统一能力稳定身份 JSON 数组；[] 表示明确无能力。 */
    private String capabilityBindings;

    /** auto=自动选择 | chat=纯对话 | react=工具循环 | orchestrated=受控子 Agent 委派 */
    private String executionMode = "auto";

    /** 当前用户的默认个人 Agent。 */
    private boolean defaultAgent;

    private boolean enabled = true;

    private String tenantId;

    private String createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public void onCreate() {
        if (agentId == null) {
            agentId = UUID.randomUUID().toString();
        }
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public String getCapabilityBindings() {
        return capabilityBindings;
    }

    public void setCapabilityBindings(String capabilityBindings) {
        this.capabilityBindings = capabilityBindings;
    }

    public String getExecutionMode() {
        return executionMode;
    }

    public void setExecutionMode(String executionMode) {
        this.executionMode = executionMode;
    }

    public boolean isChatMode() {
        return "chat".equalsIgnoreCase(executionMode);
    }

    public boolean isAutoMode() {
        return "auto".equalsIgnoreCase(executionMode);
    }

    public boolean isDefaultAgent() {
        return defaultAgent;
    }

    public void setDefaultAgent(boolean defaultAgent) {
        this.defaultAgent = defaultAgent;
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

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
