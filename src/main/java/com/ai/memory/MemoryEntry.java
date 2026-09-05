package com.ai.memory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 短期记忆和长期记忆共用的持久化元数据。
 *
 * @author data-agent
 */
@Entity
@Table(name = "memory_entry")
public class MemoryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "memory_id", length = 64)
    private String memoryId;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "user_id", length = 64, nullable = false)
    private String userId;

    @Column(name = "session_id", length = 64)
    private String sessionId;

    @Enumerated(EnumType.STRING)
    @Column(length = 32, nullable = false)
    private MemoryTier tier;

    @Enumerated(EnumType.STRING)
    @Column(length = 32, nullable = false)
    private MemoryType type;

    @Enumerated(EnumType.STRING)
    @Column(length = 32, nullable = false)
    private MemorySource source;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "compressed_content", columnDefinition = "TEXT")
    private String compressedContent;

    @Column(name = "source_content_length")
    private Long sourceContentLength = 0L;

    @Column(name = "stored_content_length")
    private Long storedContentLength = 0L;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @Column(name = "key_entities_json", columnDefinition = "TEXT")
    private String keyEntitiesJson;

    @Column(name = "topic_tags_json", columnDefinition = "TEXT")
    private String topicTagsJson;

    @Column(name = "vector_id", length = 128)
    private String vectorId;

    @Column(name = "relevance_score")
    private double relevanceScore;

    @Column(name = "access_count")
    private int accessCount;

    @Column(name = "decay_weight")
    private double decayWeight;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "last_accessed_at")
    private LocalDateTime lastAccessedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        lastAccessedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public String getMemoryId() {
        return memoryId;
    }

    public void setMemoryId(String memoryId) {
        this.memoryId = memoryId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public MemoryTier getTier() {
        return tier;
    }

    public void setTier(MemoryTier tier) {
        this.tier = tier;
    }

    public MemoryType getType() {
        return type;
    }

    public void setType(MemoryType type) {
        this.type = type;
    }

    public MemorySource getSource() {
        return source;
    }

    public void setSource(MemorySource source) {
        this.source = source;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getCompressedContent() {
        return compressedContent;
    }

    public void setCompressedContent(String compressedContent) {
        this.compressedContent = compressedContent;
    }

    public long getSourceContentLength() {
        return sourceContentLength == null ? 0L : sourceContentLength;
    }

    public void setSourceContentLength(long sourceContentLength) {
        this.sourceContentLength = sourceContentLength;
    }

    public long getStoredContentLength() {
        return storedContentLength == null ? 0L : storedContentLength;
    }

    public void setStoredContentLength(long storedContentLength) {
        this.storedContentLength = storedContentLength;
    }

    public String getMetadataJson() {
        return metadataJson;
    }

    public void setMetadataJson(String metadataJson) {
        this.metadataJson = metadataJson;
    }

    public String getKeyEntitiesJson() {
        return keyEntitiesJson;
    }

    public void setKeyEntitiesJson(String keyEntitiesJson) {
        this.keyEntitiesJson = keyEntitiesJson;
    }

    public String getTopicTagsJson() {
        return topicTagsJson;
    }

    public void setTopicTagsJson(String topicTagsJson) {
        this.topicTagsJson = topicTagsJson;
    }

    public String getVectorId() {
        return vectorId;
    }

    public void setVectorId(String vectorId) {
        this.vectorId = vectorId;
    }

    public double getRelevanceScore() {
        return relevanceScore;
    }

    public void setRelevanceScore(double relevanceScore) {
        this.relevanceScore = relevanceScore;
    }

    public int getAccessCount() {
        return accessCount;
    }

    public void setAccessCount(int accessCount) {
        this.accessCount = accessCount;
    }

    public double getDecayWeight() {
        return decayWeight;
    }

    public void setDecayWeight(double decayWeight) {
        this.decayWeight = decayWeight;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getLastAccessedAt() {
        return lastAccessedAt;
    }

    public void setLastAccessedAt(LocalDateTime lastAccessedAt) {
        this.lastAccessedAt = lastAccessedAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
}
