package com.ai.agent.eval;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/**
 * Agent Eval 的样本级结果与安全运行证据引用。
 *
 * @author data-agent
 */
@Entity
@Table(name = "agent_eval_result", indexes = {
        @Index(name = "idx_agent_eval_result_run", columnList = "tenant_id,eval_run_id,created_at"),
        @Index(name = "idx_agent_eval_result_agent_run", columnList = "tenant_id,agent_run_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_agent_eval_result_sample", columnNames = {"eval_run_id", "sample_id"})
})
public class AgentEvalResultEntity {

    @Id
    @Column(name = "result_id", nullable = false, length = 64)
    private String resultId;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "eval_run_id", nullable = false, length = 64)
    private String evalRunId;

    @Column(name = "sample_id", nullable = false, length = 64)
    private String sampleId;

    @Column(name = "sample_key", nullable = false, length = 64)
    private String sampleKey;

    @Column(name = "agent_run_id", length = 64)
    private String agentRunId;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "outcome_status", nullable = false, length = 32)
    private String outcomeStatus;

    @Column(name = "reason_code", length = 128)
    private String reasonCode;

    @Lob
    @Column(name = "outcome_evaluation_json", columnDefinition = "LONGTEXT")
    private String outcomeEvaluationJson;

    @Lob
    @Column(name = "observed_tools_json", columnDefinition = "TEXT")
    private String observedToolsJson;

    @Column(name = "approval_observed")
    private Boolean approvalObserved;

    @Column(name = "invalid_loop_observed")
    private Boolean invalidLoopObserved;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Column(name = "token_usage", nullable = false)
    private long tokenUsage;

    @Column(name = "token_usage_estimated", nullable = false)
    private boolean tokenUsageEstimated;

    @Column(name = "error_summary", length = 1024)
    private String errorSummary;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        resultId = resultId == null ? UUID.randomUUID().toString() : resultId;
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public String getResultId() { return resultId; }
    public void setResultId(String resultId) { this.resultId = resultId; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getEvalRunId() { return evalRunId; }
    public void setEvalRunId(String evalRunId) { this.evalRunId = evalRunId; }
    public String getSampleId() { return sampleId; }
    public void setSampleId(String sampleId) { this.sampleId = sampleId; }
    public String getSampleKey() { return sampleKey; }
    public void setSampleKey(String sampleKey) { this.sampleKey = sampleKey; }
    public String getAgentRunId() { return agentRunId; }
    public void setAgentRunId(String agentRunId) { this.agentRunId = agentRunId; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getOutcomeStatus() { return outcomeStatus; }
    public void setOutcomeStatus(String outcomeStatus) { this.outcomeStatus = outcomeStatus; }
    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }
    public String getOutcomeEvaluationJson() { return outcomeEvaluationJson; }
    public void setOutcomeEvaluationJson(String outcomeEvaluationJson) { this.outcomeEvaluationJson = outcomeEvaluationJson; }
    public String getObservedToolsJson() { return observedToolsJson; }
    public void setObservedToolsJson(String observedToolsJson) { this.observedToolsJson = observedToolsJson; }
    public Boolean getApprovalObserved() { return approvalObserved; }
    public void setApprovalObserved(Boolean approvalObserved) { this.approvalObserved = approvalObserved; }
    public Boolean getInvalidLoopObserved() { return invalidLoopObserved; }
    public void setInvalidLoopObserved(Boolean invalidLoopObserved) { this.invalidLoopObserved = invalidLoopObserved; }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    public long getTokenUsage() { return tokenUsage; }
    public void setTokenUsage(long tokenUsage) { this.tokenUsage = tokenUsage; }
    public boolean isTokenUsageEstimated() { return tokenUsageEstimated; }
    public void setTokenUsageEstimated(boolean tokenUsageEstimated) { this.tokenUsageEstimated = tokenUsageEstimated; }
    public String getErrorSummary() { return errorSummary; }
    public void setErrorSummary(String errorSummary) { this.errorSummary = errorSummary; }
    public Instant getCreatedAt() { return createdAt; }
}
