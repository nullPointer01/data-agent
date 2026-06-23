package com.ai.model;

import com.ai.agent.AgentType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

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

    @Column(length = 32)
    @Enumerated(EnumType.STRING)
    private AgentType type = AgentType.REACT;

    @Column(length = 512)
    private String description;

    @Column(name = "system_prompt", columnDefinition = "TEXT")
    private String systemPrompt;

    @Column(name = "model_id", length = 64)
    private String modelId;

    @Column(name = "skill_id", length = 64)
    private String skillId;

    @Column(name = "datasource_id", length = 64)
    private String datasourceId;

    /** 可用工具 JSON 数组，如 ["searchKnowledge","calculate"] */
    @Column(name = "tools", columnDefinition = "TEXT")
    private String tools;

    /** chat=纯对话 | react=工具循环 */
    @Column(name = "execution_mode", length = 16)
    private String executionMode = "react";

    @Transient
    private static final ObjectMapper TOOLS_MAPPER = new ObjectMapper();

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

    public AgentType getType() {
        return type;
    }

    public void setType(AgentType type) {
        this.type = type == null ? AgentType.REACT : type;
    }

    public void setType(String type) {
        this.type = AgentType.fromCode(type);
    }

    public String getTypeCode() {
        return type == null ? AgentType.REACT.getCode() : type.getCode();
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

    public String getSkillId() {
        return skillId;
    }

    public void setSkillId(String skillId) {
        this.skillId = skillId;
    }

    public String getDatasourceId() {
        return datasourceId;
    }

    public void setDatasourceId(String datasourceId) {
        this.datasourceId = datasourceId;
    }

    public String getTools() {
        return tools;
    }

    public void setTools(String tools) {
        this.tools = tools;
    }

    /** 解析 tools JSON 为工具名列表。 */
    public List<String> getToolList() {
        if (tools == null || tools.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return TOOLS_MAPPER.readValue(tools, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public void setToolList(List<String> toolList) {
        if (toolList == null || toolList.isEmpty()) {
            this.tools = null;
            return;
        }
        try {
            this.tools = TOOLS_MAPPER.writeValueAsString(toolList);
        } catch (Exception e) {
            this.tools = null;
        }
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
