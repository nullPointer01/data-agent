package com.ai.agent.eval;

import java.time.Instant;
import java.util.UUID;

/**
 * 一个租户内不可变编号的 Agent 评测数据集版本。
 *
 * @author data-agent
 */
public class AgentEvalDatasetEntity {

    private String datasetId;

    private String tenantId;

    private String name;

    private String description;

    private int datasetVersion;

    private String status = "DRAFT";

    private String createdBy;

    private long rowVersion;

    private Instant createdAt;

    private Instant updatedAt;

    public void onCreate() {
        Instant now = Instant.now();
        datasetId = datasetId == null ? UUID.randomUUID().toString() : datasetId;
        createdAt = createdAt == null ? now : createdAt;
        updatedAt = now;
    }

    public void onUpdate() {
        updatedAt = Instant.now();
    }

    public String getDatasetId() { return datasetId; }
    public void setDatasetId(String datasetId) { this.datasetId = datasetId; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public int getDatasetVersion() { return datasetVersion; }
    public void setDatasetVersion(int datasetVersion) { this.datasetVersion = datasetVersion; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public long getRowVersion() { return rowVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setRowVersion(long rowVersion) { this.rowVersion = rowVersion; }
}
