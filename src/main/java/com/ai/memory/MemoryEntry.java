package com.ai.memory;

import java.util.UUID;

import java.time.LocalDateTime;

/**
 * 短期记忆和长期记忆共用的持久化元数据。
 *
 * @author data-agent
 */
public class MemoryEntry {

    private String memoryId;

    private String tenantId;

    private String userId;

    private String sessionId;

    private MemoryTier tier;

    private MemoryType type;

    private MemorySource source;

    private String content;

    private String compressedContent;

    private Long sourceContentLength = 0L;

    private Long storedContentLength = 0L;

    private String metadataJson;

    private String semanticKey;

    private double confidence;

    private String keyEntitiesJson;

    private String topicTagsJson;

    /** 逻辑索引标识，不代表 Milvus 已就绪；实际处理进度由持久索引任务记录。 */
    private String vectorId;

    private double relevanceScore;

    private int accessCount;

    private double decayWeight;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime lastAccessedAt;

    private LocalDateTime expiresAt;

    public void onCreate() {
        if (memoryId == null) {
            memoryId = UUID.randomUUID().toString();
        }
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        lastAccessedAt = now;
    }

    public void onUpdate() {
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

    public String getSemanticKey() {
        return semanticKey;
    }

    public void setSemanticKey(String semanticKey) {
        this.semanticKey = semanticKey;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
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
