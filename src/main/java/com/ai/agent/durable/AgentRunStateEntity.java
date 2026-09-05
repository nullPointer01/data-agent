package com.ai.agent.durable;

import com.ai.agent.runtime.AgentExecutionMode;
import com.ai.agent.runtime.AgentRunStatus;
import com.ai.agent.runtime.AgentRunTerminationReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/**
 * 可跨进程恢复的 Agent Run 权威状态。
 *
 * @author data-agent
 */
@Entity
@Table(name = "agent_run_state", indexes = {
        @Index(name = "idx_agent_run_tenant_owner_created", columnList = "tenant_id,user_id,created_at"),
        @Index(name = "idx_agent_run_status_lease", columnList = "status,lease_until"),
        @Index(name = "idx_agent_run_approval", columnList = "approval_id")
})
public class AgentRunStateEntity {

    @Id
    @Column(name = "run_id", nullable = false, length = 64)
    private String runId;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "session_id", length = 128)
    private String sessionId;

    @Column(name = "agent_id", length = 128)
    private String agentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AgentExecutionMode mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AgentRunStatus status = AgentRunStatus.CREATED;

    @Enumerated(EnumType.STRING)
    @Column(name = "termination_reason", nullable = false, length = 64)
    private AgentRunTerminationReason terminationReason = AgentRunTerminationReason.NONE;

    @Version
    @Column(nullable = false)
    private long version;

    @Lob
    @Column(name = "checkpoint_ciphertext", columnDefinition = "LONGTEXT")
    private String checkpointCiphertext;

    @Column(name = "checkpoint_schema_version")
    private Integer checkpointSchemaVersion;

    @Column(name = "timeout_ms", nullable = false)
    private long timeoutMs;

    @Column(name = "max_iterations", nullable = false)
    private int maxIterations;

    @Column(name = "max_model_calls", nullable = false)
    private int maxModelCalls;

    @Column(name = "max_tool_calls", nullable = false)
    private int maxToolCalls;

    @Column(name = "max_tokens", nullable = false)
    private long maxTokens;

    @Column(name = "used_iterations", nullable = false)
    private int usedIterations;

    @Column(name = "used_model_calls", nullable = false)
    private int usedModelCalls;

    @Column(name = "used_tool_calls", nullable = false)
    private int usedToolCalls;

    @Column(name = "used_tokens", nullable = false)
    private long usedTokens;

    @Column(name = "token_usage_estimated", nullable = false)
    private boolean tokenUsageEstimated;

    @Column(name = "remaining_active_timeout_ms", nullable = false)
    private long remainingActiveTimeoutMs;

    @Column(name = "resume_attempts", nullable = false)
    private int resumeAttempts;

    @Column(name = "lease_owner", length = 128)
    private String leaseOwner;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "approval_id", length = 64)
    private String approvalId;

    @Column(name = "approval_expires_at")
    private Instant approvalExpiresAt;

    @Lob
    @Column(name = "result_summary", columnDefinition = "TEXT")
    private String resultSummary;

    @Column(name = "error_summary", length = 1024)
    private String errorSummary;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = createdAt == null ? now : createdAt;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
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
}
