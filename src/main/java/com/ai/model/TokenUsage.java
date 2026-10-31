package com.ai.model;

import java.time.LocalDateTime;
import java.time.LocalDate;
import com.ai.token.TokenAccountingStatus;

/**
 * 持久化的 Token 用量记账记录。
 *
 * @author data-agent
 */
public class TokenUsage {

    private Long id;

    private String userId;

    private String tenantId;

    private String modelId;

    private String modelName;

    private String skillId;

    private String skillName;

    private long promptTokens;

    private long completionTokens;

    private long totalTokens;

    private String sessionId;

    private LocalDateTime createdAt;

    private String invocationId;
    private LocalDate quotaDate;
    private TokenAccountingStatus accountingStatus = TokenAccountingStatus.LEGACY;

    /** 预占ID用于幂等；UNKNOWN 表示保守扣额，并非厂商已确认用量。 */
    public String getInvocationId() { return invocationId; }
    public void setInvocationId(String value) { invocationId = value; }
    public LocalDate getQuotaDate() { return quotaDate; }
    public void setQuotaDate(LocalDate value) { quotaDate = value; }
    public TokenAccountingStatus getAccountingStatus() { return accountingStatus; }
    public void setAccountingStatus(TokenAccountingStatus value) { accountingStatus = value; }

    public TokenUsage() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getSkillId() {
        return skillId;
    }

    public void setSkillId(String skillId) {
        this.skillId = skillId;
    }

    public String getSkillName() {
        return skillName;
    }

    public void setSkillName(String skillName) {
        this.skillName = skillName;
    }

    public long getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(long promptTokens) {
        this.promptTokens = promptTokens;
    }

    public long getCompletionTokens() {
        return completionTokens;
    }

    public void setCompletionTokens(long completionTokens) {
        this.completionTokens = completionTokens;
    }

    public long getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(long totalTokens) {
        this.totalTokens = totalTokens;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
