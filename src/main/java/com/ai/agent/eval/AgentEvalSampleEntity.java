package com.ai.agent.eval;

import java.time.Instant;
import java.util.UUID;

/**
 * 固定数据集版本中的单个 Agent 评测样本与期望标签。
 *
 * @author data-agent
 */
public class AgentEvalSampleEntity {

    private String sampleId;

    private String tenantId;

    private String datasetId;

    private int datasetVersion;

    private String sampleKey;

    private String question;

    private String expectedIntent;

    private String expectedMode;

    private String expectedContextPlanJson;

    private String taskContractJson;

    private String expectedToolsJson;

    private Boolean expectedApprovalRequired;

    private Boolean invalidLoopExpected;

    private String ragExpectedReferencesJson;

    private int sortOrder;

    private boolean enabled = true;

    private Instant createdAt;

    public void onCreate() {
        sampleId = sampleId == null ? UUID.randomUUID().toString() : sampleId;
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public String getSampleId() { return sampleId; }
    public void setSampleId(String sampleId) { this.sampleId = sampleId; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getDatasetId() { return datasetId; }
    public void setDatasetId(String datasetId) { this.datasetId = datasetId; }
    public int getDatasetVersion() { return datasetVersion; }
    public void setDatasetVersion(int datasetVersion) { this.datasetVersion = datasetVersion; }
    public String getSampleKey() { return sampleKey; }
    public void setSampleKey(String sampleKey) { this.sampleKey = sampleKey; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getExpectedIntent() { return expectedIntent; }
    public void setExpectedIntent(String expectedIntent) { this.expectedIntent = expectedIntent; }
    public String getExpectedMode() { return expectedMode; }
    public void setExpectedMode(String expectedMode) { this.expectedMode = expectedMode; }
    public String getExpectedContextPlanJson() { return expectedContextPlanJson; }
    public void setExpectedContextPlanJson(String expectedContextPlanJson) { this.expectedContextPlanJson = expectedContextPlanJson; }
    public String getTaskContractJson() { return taskContractJson; }
    public void setTaskContractJson(String taskContractJson) { this.taskContractJson = taskContractJson; }
    public String getExpectedToolsJson() { return expectedToolsJson; }
    public void setExpectedToolsJson(String expectedToolsJson) { this.expectedToolsJson = expectedToolsJson; }
    public Boolean getExpectedApprovalRequired() { return expectedApprovalRequired; }
    public void setExpectedApprovalRequired(Boolean expectedApprovalRequired) { this.expectedApprovalRequired = expectedApprovalRequired; }
    public Boolean getInvalidLoopExpected() { return invalidLoopExpected; }
    public void setInvalidLoopExpected(Boolean invalidLoopExpected) { this.invalidLoopExpected = invalidLoopExpected; }
    public String getRagExpectedReferencesJson() { return ragExpectedReferencesJson; }
    public void setRagExpectedReferencesJson(String ragExpectedReferencesJson) { this.ragExpectedReferencesJson = ragExpectedReferencesJson; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Instant getCreatedAt() { return createdAt; }
}
