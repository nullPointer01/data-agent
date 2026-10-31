package com.ai.model;

import java.util.UUID;

import java.time.LocalDateTime;

/**
 * 租户隔离的可复用技能配置。
 *
 * @author data-agent
 */
public class SkillConfig {

    private String skillId;

    private String name;

    private String description;

    private String version = "1.0";

    // 仅保留旧配置以便显式迁移与清空；官方 Skill 不执行 HTTP，也不向模型暴露这些凭据。
    private String apiUrl;

    private String apiMethod = "POST";

    private String apiHeaders;

    // 官方 Skill.content：激活后由当前 Agent 遵循的 Markdown 指令，不再独立调用模型。
    private String promptTemplate;

    // 官方 SkillResource：references/steps.md；只读参考资料，不是可执行流程定义。
    private String steps;

    private String toolDependencies = "";
    // 学习候选与个人技能始终属于创建者；人工管理的旧技能保持租户共享。
    private boolean privateSkill;
    private String reviewStatus = "PUBLISHED";
    private String sourceRunId;
    private String evidence;

    private String source = "manual";

    private Boolean enabled = true;

    private String tenantId;

    private String createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public void onCreate() {
        if (skillId == null) {
            skillId = UUID.randomUUID().toString();
        }
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public SkillConfig() {
    }

    public String getToolDependencies() { return toolDependencies; }
    public void setToolDependencies(String value) { toolDependencies = value; }
    public boolean isPrivateSkill() { return privateSkill; }
    public void setPrivateSkill(boolean value) { privateSkill = value; }
    public String getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(String value) { reviewStatus = value; }
    public String getSourceRunId() { return sourceRunId; }
    public void setSourceRunId(String value) { sourceRunId = value; }
    public String getEvidence() { return evidence; }
    public void setEvidence(String value) { evidence = value; }

    public String getSkillId() {
        return skillId;
    }

    public void setSkillId(String skillId) {
        this.skillId = skillId;
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

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public String getApiMethod() {
        return apiMethod;
    }

    public void setApiMethod(String apiMethod) {
        this.apiMethod = apiMethod;
    }

    public String getApiHeaders() {
        return apiHeaders;
    }

    public void setApiHeaders(String apiHeaders) {
        this.apiHeaders = apiHeaders;
    }

    public String getPromptTemplate() {
        return promptTemplate;
    }

    public void setPromptTemplate(String promptTemplate) {
        this.promptTemplate = promptTemplate;
    }

    public String getSteps() {
        return steps;
    }

    public void setSteps(String steps) {
        this.steps = steps;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public boolean isEnabled() {
        return enabled != null ? enabled : false;
    }

    public void setEnabled(Boolean enabled) {
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
