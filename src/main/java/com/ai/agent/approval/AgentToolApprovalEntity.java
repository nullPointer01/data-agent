package com.ai.agent.approval;

import com.ai.agent.tool.governance.AgentToolRiskLevel;

import java.time.Instant;

/**
 * 单个待执行工具动作的持久化审批记录。
 *
 * @author data-agent
 */
public class AgentToolApprovalEntity {

    private String approvalId;

    private String tenantId;

    private String runId;

    private String toolCallId;

    private String toolName;

    private AgentToolRiskLevel riskLevel;

    private String requesterUserId;

    private String approvalPermission;

    private String safeArgumentSummary;

    private String requestCiphertext;

    private AgentApprovalDecisionStatus decisionStatus = AgentApprovalDecisionStatus.PENDING;

    private AgentApprovalExecutionStatus executionStatus = AgentApprovalExecutionStatus.WAITING_DECISION;

    private long version;

    private String reviewerUserId;

    private String decisionComment;

    private Instant requestedAt;

    private Instant expiresAt;

    private Instant decidedAt;

    private Instant executionStartedAt;

    private Instant executionCompletedAt;

    private Instant updatedAt;

    public void onCreate() {
        Instant now = Instant.now();
        requestedAt = requestedAt == null ? now : requestedAt;
        updatedAt = now;
    }

    public void onUpdate() {
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
    public void setVersion(long version) { this.version = version; }
}
