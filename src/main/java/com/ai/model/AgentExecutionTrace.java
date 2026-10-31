package com.ai.model;

import java.time.LocalDateTime;

/**
 * 统一 Agent Run 执行轨迹。
 *
 * @author data-agent
 */
public class AgentExecutionTrace {

    private Long id;

    private String traceId;

    private String tenantId;

    private String userId;

    private String sessionId;

    private String selectedAgent;

    /** 历史字段名，当前记录 Chat、ReAct 或 Orchestrated 运行模式。 */
    private String selectedType;

    private String intent;

    private String complexity;

    private boolean success;

    private boolean fallbackUsed;

    private int taskCount;

    private long durationMs;

    private String question;

    private String reason;

    private String error;

    private String planJson;

    private String taskResultsJson;

    private String sharedContextJson;

    private LocalDateTime createdAt;

    public void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
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

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getSelectedAgent() {
        return selectedAgent;
    }

    public void setSelectedAgent(String selectedAgent) {
        this.selectedAgent = selectedAgent;
    }

    public String getSelectedType() {
        return selectedType;
    }

    public void setSelectedType(String selectedType) {
        this.selectedType = selectedType;
    }

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public String getComplexity() {
        return complexity;
    }

    public void setComplexity(String complexity) {
        this.complexity = complexity;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public boolean isFallbackUsed() {
        return fallbackUsed;
    }

    public void setFallbackUsed(boolean fallbackUsed) {
        this.fallbackUsed = fallbackUsed;
    }

    public int getTaskCount() {
        return taskCount;
    }

    public void setTaskCount(int taskCount) {
        this.taskCount = taskCount;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getPlanJson() {
        return planJson;
    }

    public void setPlanJson(String planJson) {
        this.planJson = planJson;
    }

    public String getTaskResultsJson() {
        return taskResultsJson;
    }

    public void setTaskResultsJson(String taskResultsJson) {
        this.taskResultsJson = taskResultsJson;
    }

    public String getSharedContextJson() {
        return sharedContextJson;
    }

    public void setSharedContextJson(String sharedContextJson) {
        this.sharedContextJson = sharedContextJson;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
