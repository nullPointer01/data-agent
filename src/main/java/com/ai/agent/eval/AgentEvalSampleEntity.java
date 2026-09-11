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
 * 固定数据集版本中的单个 Agent 评测样本与期望标签。
 *
 * @author data-agent
 */
@Entity
@Table(name = "agent_eval_sample", indexes = {
        @Index(name = "idx_agent_eval_sample_dataset", columnList = "tenant_id,dataset_id,dataset_version,sort_order")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_agent_eval_sample_key", columnNames = {"dataset_id", "sample_key"})
})
public class AgentEvalSampleEntity {

    @Id
    @Column(name = "sample_id", nullable = false, length = 64)
    private String sampleId;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "dataset_id", nullable = false, length = 64)
    private String datasetId;

    @Column(name = "dataset_version", nullable = false)
    private int datasetVersion;

    @Column(name = "sample_key", nullable = false, length = 64)
    private String sampleKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String question;

    @Lob
    @Column(name = "task_contract_json", nullable = false, columnDefinition = "LONGTEXT")
    private String taskContractJson;

    @Lob
    @Column(name = "expected_tools_json", columnDefinition = "TEXT")
    private String expectedToolsJson;

    @Column(name = "expected_approval_required")
    private Boolean expectedApprovalRequired;

    @Column(name = "invalid_loop_expected")
    private Boolean invalidLoopExpected;

    @Lob
    @Column(name = "rag_expected_references_json", columnDefinition = "TEXT")
    private String ragExpectedReferencesJson;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
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
