package com.ai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 租户隔离的可配置 Agent。
 *
 * @author data-agent
 */
@Entity
@Table(name = "agent_profile")
public class AgentProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "agent_id", length = 64)
    private String agentId;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(length = 512)
    private String description;

    @Column(name = "system_prompt", columnDefinition = "TEXT")
    private String systemPrompt;

    @Column(name = "model_id", length = 64)
    private String modelId;

    /** 统一能力稳定身份 JSON 数组；[] 表示明确无能力。 */
    @Column(name = "capability_bindings", nullable = false, columnDefinition = "TEXT")
    private String capabilityBindings;

    /** auto=自动选择 | chat=纯对话 | react=工具循环 | orchestrated=受控子 Agent 委派 */
    @Column(name = "execution_mode", length = 16)
    private String executionMode = "auto";

    /** 当前用户的默认个人 Agent。 */
    @Column(name = "default_agent", nullable = false, columnDefinition = "BIT(1) NOT NULL DEFAULT 0")
    private boolean defaultAgent;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "tenant_id", length = 64)
    private String tenantId;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
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
