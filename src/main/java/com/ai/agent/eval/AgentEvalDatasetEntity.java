package com.ai.agent.eval;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/**
 * 一个租户内不可变编号的 Agent 评测数据集版本。
 *
 * @author data-agent
 */
@Entity
@Table(name = "agent_eval_dataset", indexes = {
        @Index(name = "idx_agent_eval_dataset_tenant_status", columnList = "tenant_id,status,updated_at")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_agent_eval_dataset_name_version", columnNames = {"tenant_id", "name", "dataset_version"})
})
public class AgentEvalDatasetEntity {

    @Id
    @Column(name = "dataset_id", nullable = false, length = 64)
    private String datasetId;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 512)
    private String description;

    @Column(name = "dataset_version", nullable = false)
    private int datasetVersion;

    @Column(nullable = false, length = 32)
    private String status = "DRAFT";

    @Column(name = "created_by", nullable = false, length = 64)
    private String createdBy;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        datasetId = datasetId == null ? UUID.randomUUID().toString() : datasetId;
        createdAt = createdAt == null ? now : createdAt;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
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
}
