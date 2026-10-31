package com.ai.agent.durable;

import com.ai.agent.runtime.AgentExecutionMode;
import com.ai.agent.runtime.AgentRunStatus;
import com.ai.agent.runtime.AgentRunTerminationReason;

import java.time.Instant;

/**
 * 可跨进程恢复的 Agent Run 权威状态。
 *
 * @author data-agent
 */
public class AgentRunStateEntity {

    private String runId;

    private String tenantId;

    private String userId;

    private String sessionId;

    private String agentId;

    private AgentExecutionMode mode;

    private AgentRunStatus status = AgentRunStatus.CREATED;

    private AgentRunTerminationReason terminationReason = AgentRunTerminationReason.NONE;

    private long version;

    private String checkpointCiphertext;

    private Integer checkpointSchemaVersion;

    private long timeoutMs;

    private int maxIterations;

    private int maxModelCalls;

    private int maxToolCalls;

    private long maxTokens;

    private int usedIterations;

    private int usedModelCalls;

    private int usedToolCalls;

    private long usedTokens;

    private boolean tokenUsageEstimated;

    private long remainingActiveTimeoutMs;

    private int resumeAttempts;

    private String leaseOwner;

    private Instant leaseUntil;

    private String approvalId;

    private Instant approvalExpiresAt;

    private String resultSummary;

    private String errorSummary;

    private Instant createdAt;

    private Instant updatedAt;

    private Instant completedAt;

    public void onCreate() {
        Instant now = Instant.now();
        createdAt = createdAt == null ? now : createdAt;
        updatedAt = now;
    }

    public void onUpdate() {
        updatedAt = Instant.now();
    }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public AgentExecutionMode getMode() { return mode; }
    public void setMode(AgentExecutionMode mode) { this.mode = mode; }
    public AgentRunStatus getStatus() { return status; }
    public void setStatus(AgentRunStatus status) { this.status = status; }
    public AgentRunTerminationReason getTerminationReason() { return terminationReason; }
    public void setTerminationReason(AgentRunTerminationReason terminationReason) { this.terminationReason = terminationReason; }
    public long getVersion() { return version; }
    public String getCheckpointCiphertext() { return checkpointCiphertext; }
    public void setCheckpointCiphertext(String checkpointCiphertext) { this.checkpointCiphertext = checkpointCiphertext; }
    public Integer getCheckpointSchemaVersion() { return checkpointSchemaVersion; }
    public void setCheckpointSchemaVersion(Integer checkpointSchemaVersion) { this.checkpointSchemaVersion = checkpointSchemaVersion; }
    public long getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }
    public int getMaxIterations() { return maxIterations; }
    public void setMaxIterations(int maxIterations) { this.maxIterations = maxIterations; }
    public int getMaxModelCalls() { return maxModelCalls; }
    public void setMaxModelCalls(int maxModelCalls) { this.maxModelCalls = maxModelCalls; }
    public int getMaxToolCalls() { return maxToolCalls; }
    public void setMaxToolCalls(int maxToolCalls) { this.maxToolCalls = maxToolCalls; }
    public long getMaxTokens() { return maxTokens; }
    public void setMaxTokens(long maxTokens) { this.maxTokens = maxTokens; }
    public int getUsedIterations() { return usedIterations; }
    public void setUsedIterations(int usedIterations) { this.usedIterations = usedIterations; }
    public int getUsedModelCalls() { return usedModelCalls; }
    public void setUsedModelCalls(int usedModelCalls) { this.usedModelCalls = usedModelCalls; }
    public int getUsedToolCalls() { return usedToolCalls; }
    public void setUsedToolCalls(int usedToolCalls) { this.usedToolCalls = usedToolCalls; }
    public long getUsedTokens() { return usedTokens; }
    public void setUsedTokens(long usedTokens) { this.usedTokens = usedTokens; }
    public boolean isTokenUsageEstimated() { return tokenUsageEstimated; }
    public void setTokenUsageEstimated(boolean tokenUsageEstimated) { this.tokenUsageEstimated = tokenUsageEstimated; }
    public long getRemainingActiveTimeoutMs() { return remainingActiveTimeoutMs; }
    public void setRemainingActiveTimeoutMs(long remainingActiveTimeoutMs) { this.remainingActiveTimeoutMs = remainingActiveTimeoutMs; }
    public int getResumeAttempts() { return resumeAttempts; }
    public void setResumeAttempts(int resumeAttempts) { this.resumeAttempts = resumeAttempts; }
    public String getLeaseOwner() { return leaseOwner; }
    public void setLeaseOwner(String leaseOwner) { this.leaseOwner = leaseOwner; }
    public Instant getLeaseUntil() { return leaseUntil; }
    public void setLeaseUntil(Instant leaseUntil) { this.leaseUntil = leaseUntil; }
    public String getApprovalId() { return approvalId; }
    public void setApprovalId(String approvalId) { this.approvalId = approvalId; }
    public Instant getApprovalExpiresAt() { return approvalExpiresAt; }
    public void setApprovalExpiresAt(Instant approvalExpiresAt) { this.approvalExpiresAt = approvalExpiresAt; }
    public String getResultSummary() { return resultSummary; }
    public void setResultSummary(String resultSummary) { this.resultSummary = resultSummary; }
    public String getErrorSummary() { return errorSummary; }
    public void setErrorSummary(String errorSummary) { this.errorSummary = errorSummary; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public void setVersion(long version) { this.version = version; }
}
