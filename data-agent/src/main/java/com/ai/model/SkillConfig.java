package com.ai.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "skill_config")
public class SkillConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String skillId;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(length = 512)
    private String description;

    @Column(length = 16)
    private String version = "1.0";

    @Column(name = "api_url", length = 256)
    private String apiUrl;

    @Column(name = "api_method", length = 8)
    private String apiMethod = "POST";

    @Column(name = "api_headers", columnDefinition = "TEXT")
    private String apiHeaders;

    @Column(name = "prompt_template", columnDefinition = "TEXT")
    private String promptTemplate;

    @Column(name = "response_template", columnDefinition = "TEXT")
    private String responseTemplate;

    @Column(name = "keywords", length = 512)
    private String keywords;

    @Column(name = "steps", columnDefinition = "TEXT")
    private String steps;

    @Column(name = "auto_attach", length = 64)
    private String autoAttach;

    @Column(name = "source", length = 32)
    private String source = "manual";

    @Column(name = "feedback_count")
    private int feedbackCount = 0;

    @Column(name = "positive_count")
    private int positiveCount = 0;

    private boolean enabled = true;

    @Column(name = "is_default")
    private boolean isDefault = false;

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
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public SkillConfig() {
    }

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

    public String getResponseTemplate() {
        return responseTemplate;
    }

    public void setResponseTemplate(String responseTemplate) {
        this.responseTemplate = responseTemplate;
    }

    public String getKeywords() {
        return keywords;
    }

    public void setKeywords(String keywords) {
        this.keywords = keywords;
    }

    public String getSteps() {
        return steps;
    }

    public void setSteps(String steps) {
        this.steps = steps;
    }

    public String getAutoAttach() {
        return autoAttach;
    }

    public void setAutoAttach(String autoAttach) {
        this.autoAttach = autoAttach;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public int getFeedbackCount() {
        return feedbackCount;
    }

    public void setFeedbackCount(int feedbackCount) {
        this.feedbackCount = feedbackCount;
    }

    public int getPositiveCount() {
        return positiveCount;
    }

    public void setPositiveCount(int positiveCount) {
        this.positiveCount = positiveCount;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isDefault() {
        return isDefault;
    }

    @JsonProperty("isDefault")
    public void setDefault(boolean aDefault) {
        isDefault = aDefault;
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
