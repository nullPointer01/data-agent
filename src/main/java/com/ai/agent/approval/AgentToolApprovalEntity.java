package com.ai.agent.approval;

import com.ai.agent.tool.governance.AgentToolRiskLevel;
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
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.Instant;

/**
 * 单个待执行工具动作的持久化审批记录。
 *
 * @author data-agent
 */
@Entity
@Table(name = "agent_tool_approval", indexes = {
        @Index(name = "idx_agent_approval_tenant_status_created", columnList = "tenant_id,decision_status,requested_at"),
        @Index(name = "idx_agent_approval_pending_expiry", columnList = "decision_status,expires_at"),
        @Index(name = "idx_agent_approval_run", columnList = "tenant_id,run_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_agent_approval_run_tool_call",
                columnNames = {"tenant_id", "run_id", "tool_call_id"})
})
public class AgentToolApprovalEntity {

    @Id
    @Column(name = "approval_id", nullable = false, length = 64)
    private String approvalId;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "run_id", nullable = false, length = 64)
    private String runId;

    @Column(name = "tool_call_id", nullable = false, length = 128)
    private String toolCallId;

    @Column(name = "tool_name", nullable = false, length = 128)
    private String toolName;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 16)
    private AgentToolRiskLevel riskLevel;

    @Column(name = "requester_user_id", nullable = false, length = 64)
    private String requesterUserId;

    @Column(name = "approval_permission", nullable = false, length = 128)
    private String approvalPermission;

    @Column(name = "safe_argument_summary", nullable = false, length = 2048)
    private String safeArgumentSummary;

    @Lob
    @Column(name = "request_ciphertext", nullable = false, columnDefinition = "LONGTEXT")
    private String requestCiphertext;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision_status", nullable = false, length = 32)
    private AgentApprovalDecisionStatus decisionStatus = AgentApprovalDecisionStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_status", nullable = false, length = 32)
    private AgentApprovalExecutionStatus executionStatus = AgentApprovalExecutionStatus.WAITING_DECISION;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "reviewer_user_id", length = 64)
    private String reviewerUserId;

    @Column(name = "decision_comment", length = 512)
    private String decisionComment;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "execution_started_at")
    private Instant executionStartedAt;

    @Column(name = "execution_completed_at")
    private Instant executionCompletedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        requestedAt = requestedAt == null ? now : requestedAt;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public String getApprovalId() { return approvalId; }
    public void setApprovalId(String approvalId) { this.approvalId = approvalId; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }
    public String getToolCallId() { return toolCallId; }
    public void setToolCallId(String toolCallId) { this.toolCallId = toolCallId; }
    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }
    public AgentToolRiskLevel getRiskLevel() { return riskLevel; }
    public void setRiskLevel(AgentToolRiskLevel riskLevel) { this.riskLevel = riskLevel; }
    public String getRequesterUserId() { return requesterUserId; }
    public void setRequesterUserId(String requesterUserId) { this.requesterUserId = requesterUserId; }
    public String getApprovalPermission() { return approvalPermission; }
    public void setApprovalPermission(String approvalPermission) { this.approvalPermission = approvalPermission; }
    public String getSafeArgumentSummary() { return safeArgumentSummary; }
    public void setSafeArgumentSummary(String safeArgumentSummary) { this.safeArgumentSummary = safeArgumentSummary; }
    public String getRequestCiphertext() { return requestCiphertext; }
    public void setRequestCiphertext(String requestCiphertext) { this.requestCiphertext = requestCiphertext; }
    public AgentApprovalDecisionStatus getDecisionStatus() { return decisionStatus; }
    public void setDecisionStatus(AgentApprovalDecisionStatus decisionStatus) { this.decisionStatus = decisionStatus; }
    public AgentApprovalExecutionStatus getExecutionStatus() { return executionStatus; }
    public void setExecutionStatus(AgentApprovalExecutionStatus executionStatus) { this.executionStatus = executionStatus; }
    public long getVersion() { return version; }
    public String getReviewerUserId() { return reviewerUserId; }
    public void setReviewerUserId(String reviewerUserId) { this.reviewerUserId = reviewerUserId; }
    public String getDecisionComment() { return decisionComment; }
    public void setDecisionComment(String decisionComment) { this.decisionComment = decisionComment; }
    public Instant getRequestedAt() { return requestedAt; }
    public void setRequestedAt(Instant requestedAt) { this.requestedAt = requestedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }
    public Instant getExecutionStartedAt() { return executionStartedAt; }
    public void setExecutionStartedAt(Instant executionStartedAt) { this.executionStartedAt = executionStartedAt; }
    public Instant getExecutionCompletedAt() { return executionCompletedAt; }
    public void setExecutionCompletedAt(Instant executionCompletedAt) { this.executionCompletedAt = executionCompletedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
