package com.ai.agent.eval;

import java.time.Instant;
import java.util.UUID;

/**
 * 一次绑定固定数据集、Agent、模型和 Harness 配置的评测运行。
 *
 * @author data-agent
 */
public class AgentEvalRunEntity {

    private String evalRunId;

    private String tenantId;

    private String datasetId;

    private int datasetVersion;

    private String agentId;

    private String agentProfileVersion;

    private String modelId;

    private String harnessConfigIdentity;

    private String status = "PENDING";

    private int totalSamples;

    private int completedSamples;

    private int passedSamples;

    private int failedSamples;

    private int notEvaluatedSamples;

    private String metricsJson;

    private String errorSummary;

    private String createdBy;

    private long rowVersion;

    private Instant startedAt;

    private Instant completedAt;

    public void onCreate() {
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
    public void setRowVersion(long rowVersion) { this.rowVersion = rowVersion; }
}
