package com.ai.agent.eval;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/**
 * 一次绑定固定数据集、Agent、模型和 Harness 配置的评测运行。
 *
 * @author data-agent
 */
@Entity
@Table(name = "agent_eval_run", indexes = {
        @Index(name = "idx_agent_eval_run_tenant_started", columnList = "tenant_id,started_at"),
        @Index(name = "idx_agent_eval_run_dataset", columnList = "tenant_id,dataset_id,dataset_version")
})
public class AgentEvalRunEntity {

    @Id
    @Column(name = "eval_run_id", nullable = false, length = 64)
    private String evalRunId;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "dataset_id", nullable = false, length = 64)
    private String datasetId;

    @Column(name = "dataset_version", nullable = false)
    private int datasetVersion;

    @Column(name = "agent_id", nullable = false, length = 64)
    private String agentId;

    @Column(name = "agent_profile_version", nullable = false, length = 64)
    private String agentProfileVersion;

    @Column(name = "model_id", length = 64)
    private String modelId;

    @Column(name = "harness_config_identity", nullable = false, length = 128)
    private String harnessConfigIdentity;

    @Column(nullable = false, length = 32)
    private String status = "PENDING";

    @Column(name = "total_samples", nullable = false)
    private int totalSamples;

    @Column(name = "completed_samples", nullable = false)
    private int completedSamples;

    @Column(name = "passed_samples", nullable = false)
    private int passedSamples;

    @Column(name = "failed_samples", nullable = false)
    private int failedSamples;

    @Column(name = "not_evaluated_samples", nullable = false)
    private int notEvaluatedSamples;

    @Lob
    @Column(name = "metrics_json", columnDefinition = "LONGTEXT")
    private String metricsJson;

    @Column(name = "error_summary", length = 1024)
    private String errorSummary;

    @Column(name = "created_by", nullable = false, length = 64)
    private String createdBy;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @PrePersist
    protected void onCreate() {
        evalRunId = evalRunId == null ? UUID.randomUUID().toString() : evalRunId;
        startedAt = startedAt == null ? Instant.now() : startedAt;
    }

    public String getEvalRunId() { return evalRunId; }
    public void setEvalRunId(String evalRunId) { this.evalRunId = evalRunId; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getDatasetId() { return datasetId; }
    public void setDatasetId(String datasetId) { this.datasetId = datasetId; }
    public int getDatasetVersion() { return datasetVersion; }
    public void setDatasetVersion(int datasetVersion) { this.datasetVersion = datasetVersion; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getAgentProfileVersion() { return agentProfileVersion; }
    public void setAgentProfileVersion(String agentProfileVersion) { this.agentProfileVersion = agentProfileVersion; }
    public String getModelId() { return modelId; }
    public void setModelId(String modelId) { this.modelId = modelId; }
    public String getHarnessConfigIdentity() { return harnessConfigIdentity; }
    public void setHarnessConfigIdentity(String harnessConfigIdentity) { this.harnessConfigIdentity = harnessConfigIdentity; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getTotalSamples() { return totalSamples; }
    public void setTotalSamples(int totalSamples) { this.totalSamples = totalSamples; }
    public int getCompletedSamples() { return completedSamples; }
    public void setCompletedSamples(int completedSamples) { this.completedSamples = completedSamples; }
    public int getPassedSamples() { return passedSamples; }
    public void setPassedSamples(int passedSamples) { this.passedSamples = passedSamples; }
    public int getFailedSamples() { return failedSamples; }
    public void setFailedSamples(int failedSamples) { this.failedSamples = failedSamples; }
    public int getNotEvaluatedSamples() { return notEvaluatedSamples; }
    public void setNotEvaluatedSamples(int notEvaluatedSamples) { this.notEvaluatedSamples = notEvaluatedSamples; }
    public String getMetricsJson() { return metricsJson; }
    public void setMetricsJson(String metricsJson) { this.metricsJson = metricsJson; }
    public String getErrorSummary() { return errorSummary; }
    public void setErrorSummary(String errorSummary) { this.errorSummary = errorSummary; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public long getRowVersion() { return rowVersion; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
