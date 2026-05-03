package com.ai.model;

import jakarta.persistence.*;
import java.util.Date;

@Entity
@Table(name = "knowledge_entry")
public class KnowledgeEntry {

    @Id
    @Column(length = 64)
    private String knowledgeId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(length = 50)
    private String sourceType;

    @Column(length = 500)
    private String sourceFilename;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String content;

    private int chunkCount;

    private long contentLength;

    @Column(length = 100)
    private String tenantId;

    @Column(length = 100)
    private String createdBy;

    @Column(updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = new Date();
        if (knowledgeId == null) knowledgeId = java.util.UUID.randomUUID().toString();
    }

    public String getKnowledgeId() { return knowledgeId; }
    public void setKnowledgeId(String knowledgeId) { this.knowledgeId = knowledgeId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getSourceFilename() { return sourceFilename; }
    public void setSourceFilename(String sourceFilename) { this.sourceFilename = sourceFilename; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public int getChunkCount() { return chunkCount; }
    public void setChunkCount(int chunkCount) { this.chunkCount = chunkCount; }
    public long getContentLength() { return contentLength; }
    public void setContentLength(long contentLength) { this.contentLength = contentLength; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}
