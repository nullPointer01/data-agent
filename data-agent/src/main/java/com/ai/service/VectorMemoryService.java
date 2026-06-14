package com.ai.service;

import com.ai.vector.EmbeddingGateway;
import com.ai.vector.MilvusVectorStoreGateway;
import com.ai.vector.TextChunker;
import com.ai.vector.VectorChunk;
import com.ai.vector.VectorDocumentIndexer;
import com.ai.vector.VectorDocumentTypes;
import com.ai.vector.VectorIdentity;
import com.ai.vector.VectorIdentityNormalizer;
import com.ai.vector.VectorIndexRegistry;
import com.ai.vector.VectorMetadataFilters;
import com.ai.vector.VectorMetadataKeys;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.filter.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 面向 Agent 检索和长期记忆的向量服务门面。
 *
 * @author data-agent
 */
@Service
public class VectorMemoryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(VectorMemoryService.class);
    private static final double DEFAULT_MIN_SCORE = 0.5D;
    private static final int QUERY_LOG_PREVIEW_LENGTH = 50;
    private static final String USER_LABEL = "用户: ";
    private static final String ASSISTANT_LABEL = "\n助手: ";
    private static final String FILE_LABEL_PREFIX = "文件[";
    private static final String FILE_LABEL_SUFFIX = "] ";
    private static final String MEMORY_LABEL_PREFIX = "记忆: ";
    private static final String SKILL_LABEL_PREFIX = "技能[";
    private static final String SKILL_LABEL_MIDDLE = "]: ";
    private static final String SKILL_TEMPLATE_SEPARATOR = " | 模板: ";

    private final TextChunker textChunker;

    private final EmbeddingGateway embeddingGateway;

    private final MilvusVectorStoreGateway vectorStoreGateway;

    private final VectorDocumentIndexer vectorDocumentIndexer;

    private final VectorIndexRegistry vectorIndexRegistry;

    private final VectorIdentityNormalizer identityNormalizer;

    public VectorMemoryService(TextChunker textChunker, EmbeddingGateway embeddingGateway,
            MilvusVectorStoreGateway vectorStoreGateway, VectorDocumentIndexer vectorDocumentIndexer,
            VectorIndexRegistry vectorIndexRegistry, VectorIdentityNormalizer identityNormalizer) {
        this.textChunker = textChunker;
        this.embeddingGateway = embeddingGateway;
        this.vectorStoreGateway = vectorStoreGateway;
        this.vectorDocumentIndexer = vectorDocumentIndexer;
        this.vectorIndexRegistry = vectorIndexRegistry;
        this.identityNormalizer = identityNormalizer;
    }

    public boolean isUsingMilvus() {
        return vectorStoreGateway.isAvailable();
    }

    public boolean isVectorStoreAvailable() {
        return vectorStoreGateway.isAvailable();
    }

    public void indexConversation(String sessionId, String userMessage, String assistantReply) {
        indexConversation(sessionId, userMessage, assistantReply, null, null);
    }

    public void indexConversation(String sessionId, String userMessage, String assistantReply,
            String tenantId, String userId) {
        VectorIdentity identity = identityNormalizer.normalize(tenantId, userId);
        String summary = USER_LABEL + userMessage + ASSISTANT_LABEL + assistantReply;
        String entryId = VectorDocumentTypes.CONVERSATION + ":" + sessionId + ":" + System.currentTimeMillis();
        index(summary, VectorDocumentTypes.CONVERSATION, entryId, sessionId, identity);
        LOGGER.debug("已索引会话向量: {}", sessionId);
    }

    public void indexFile(String fileId, String filename, String content) {
        indexFile(fileId, filename, content, null, null);
    }

    public void indexFile(String fileId, String filename, String content, String tenantId, String userId) {
        if (!hasText(content)) {
            return;
        }
        VectorIdentity identity = identityNormalizer.normalize(tenantId, userId);
        removeFromStore(VectorDocumentTypes.FILE, fileId, identity.tenantId());

        List<VectorChunk> chunks = textChunker.split(fileId, content);
        for (VectorChunk chunk : chunks) {
            String text = FILE_LABEL_PREFIX + filename + FILE_LABEL_SUFFIX + chunk.text();
            index(text, VectorDocumentTypes.FILE, chunk, identity);
        }
        LOGGER.debug("已索引文件: {} ({} 个分块)", filename, chunks.size());
    }

    public void indexSkill(String skillName, String description, String promptTemplate) {
        VectorIdentity identity = identityNormalizer.normalize(null, null);
        String text = SKILL_LABEL_PREFIX + skillName + SKILL_LABEL_MIDDLE + description
                + SKILL_TEMPLATE_SEPARATOR + promptTemplate;
        index(text, VectorDocumentTypes.SKILL, skillName, skillName, identity);
        LOGGER.debug("已索引技能: {}", skillName);
    }

    public void indexKnowledge(String content, String source) {
        indexKnowledge(content, source, null, null);
    }

    public void indexKnowledge(String content, String source, String tenantId, String userId) {
        if (!hasText(content)) {
            return;
        }
        VectorIdentity identity = identityNormalizer.normalize(tenantId, userId);
        removeFromStore(VectorDocumentTypes.KNOWLEDGE, source, identity.tenantId());

        List<VectorChunk> chunks = textChunker.split(source, content);
        for (VectorChunk chunk : chunks) {
            index(chunk.text(), VectorDocumentTypes.KNOWLEDGE, chunk, identity);
        }
        LOGGER.debug("已索引知识: {} ({} 个分块)", source, chunks.size());
    }

    /**
     * 将持久化用户记忆索引到 Milvus，用于长期召回。
     *
     * @param memoryId 记忆编号
     * @param content 记忆文本
     * @param tenantId 租户编号
     * @param userId 用户编号
     */
    public void indexMemory(String memoryId, String content, String tenantId, String userId) {
        if (!hasText(content)) {
            return;
        }
        VectorIdentity identity = identityNormalizer.normalize(tenantId, userId);
        removeFromStore(VectorDocumentTypes.MEMORY, memoryId, identity.tenantId());

        List<VectorChunk> chunks = textChunker.split(memoryId, content);
        for (VectorChunk chunk : chunks) {
            index(MEMORY_LABEL_PREFIX + chunk.text(), VectorDocumentTypes.MEMORY, chunk, identity);
        }
        LOGGER.debug("已索引长期记忆: {} ({} 个分块)", memoryId, chunks.size());
    }

    public int estimateChunkCount(String content) {
        return textChunker.estimateChunkCount(content);
    }

    public String searchRelevant(String query, int topK) {
        return searchRelevant(query, topK, DEFAULT_MIN_SCORE, null);
    }

    public String searchRelevant(String query, int topK, double minScore) {
        return searchRelevant(query, topK, minScore, null);
    }

    public String searchRelevant(String query, int topK, double minScore, String tenantId) {
        return searchRelevant(query, topK, minScore, tenantId, List.of());
    }

    /**
     * 检索向量上下文并格式化用于提示词注入。
     *
     * @param query 查询文本
     * @param topK 返回结果数量
     * @param minScore 最小向量相似度分数
     * @param tenantId 租户编号
     * @param sourceTypes 可选来源类型过滤
     * @return 格式化后的相关上下文
     */
    public String searchRelevant(String query, int topK, double minScore, String tenantId,
            List<String> sourceTypes) {
        List<EmbeddingMatch<TextSegment>> matches = searchMatches(query, topK, minScore, tenantId, null,
                sourceTypes);
        if (matches.isEmpty()) {
            LOGGER.debug("未找到相关结果，查询: {}", preview(query, QUERY_LOG_PREVIEW_LENGTH));
            return "";
        }
        return matches.stream()
                .map(this::formatMatch)
                .collect(Collectors.joining("\n\n"));
    }

    /**
     * 带租户和用户隔离的向量上下文检索，格式化匹配结果用于提示词注入。
     *
     * @param query 查询文本
     * @param topK 返回结果数量
     * @param minScore 最小向量相似度分数
     * @param tenantId 租户编号
     * @param userId 用户编号
     * @param sourceTypes 可选来源类型过滤
     * @return 格式化后的相关上下文
     */
    public String searchRelevant(String query, int topK, double minScore, String tenantId, String userId,
            List<String> sourceTypes) {
        List<EmbeddingMatch<TextSegment>> matches = searchMatches(query, topK, minScore, tenantId, userId,
                sourceTypes);
        if (matches.isEmpty()) {
            LOGGER.debug("未找到相关结果，查询: {}", preview(query, QUERY_LOG_PREVIEW_LENGTH));
            return "";
        }
        return matches.stream()
                .map(this::formatMatch)
                .collect(Collectors.joining("\n\n"));
    }

    public List<EmbeddingMatch<TextSegment>> searchMatches(String query, int topK, double minScore) {
        return searchMatches(query, topK, minScore, null);
    }

    public List<EmbeddingMatch<TextSegment>> searchMatches(String query, int topK, double minScore, String tenantId) {
        return searchMatches(query, topK, minScore, tenantId, List.of());
    }

    public List<EmbeddingMatch<TextSegment>> searchMatches(String query, int topK, double minScore, String tenantId,
            List<String> sourceTypes) {
        return searchMatches(query, topK, minScore, tenantId, null, sourceTypes);
    }

    public List<EmbeddingMatch<TextSegment>> searchMatches(String query, int topK, double minScore, String tenantId,
            String userId, List<String> sourceTypes) {
        if (!hasText(query)) {
            return List.of();
        }
        try {
            Embedding queryEmbedding = embeddingGateway.embed(query);
            Filter filter = VectorMetadataFilters.searchFilter(tenantId, userId, sourceTypes);
            return vectorStoreGateway.search(queryEmbedding, topK, minScore, filter);
        } catch (Exception e) {
            LOGGER.warn("Search matches failed: {}", e.getMessage(), e);
            return List.of();
        }
    }

    public synchronized void removeFromStore(String type, String id) {
        removeFromStore(type, id, null);
    }

    public synchronized void removeFromStore(String type, String id, String tenantId) {
        vectorStoreGateway.deleteBySource(type, id, tenantId);
        int removedCount = vectorIndexRegistry.removeBySource(type, id, tenantId);
        LOGGER.info("Removed {} entries from Milvus - type: {}, id prefix: {}, tenant: {}",
                removedCount, type, id, tenantId);
    }

    public int getIndexedCount() {
        return vectorIndexRegistry.getIndexedCount();
    }

    public Map<String, Long> getIndexedCountByType() {
        return vectorIndexRegistry.getIndexedCountByType();
    }

    private void index(String text, String type, String id, String sourceId, VectorIdentity identity) {
        try {
            if (!vectorStoreGateway.isAvailable()) {
                LOGGER.debug("向量存储不可用，跳过索引写入: type={}, id={}", type, id);
                return;
            }
            vectorDocumentIndexer.index(text, type, id, sourceId, identity.tenantId(), identity.userId());
        } catch (Exception e) {
            LOGGER.warn("向量索引写入失败 [{}]: {}", type, e.getMessage(), e);
        }
    }

    private void index(String text, String type, VectorChunk chunk, VectorIdentity identity) {
        try {
            if (!vectorStoreGateway.isAvailable()) {
                LOGGER.debug("向量存储不可用，跳过索引写入: type={}, id={}", type, chunk.id());
                return;
            }
            vectorDocumentIndexer.index(text, type, chunk, identity.tenantId(), identity.userId());
        } catch (Exception e) {
            LOGGER.warn("向量索引写入失败 [{}]: {}", type, e.getMessage(), e);
        }
    }

    private String formatMatch(EmbeddingMatch<TextSegment> match) {
        double score = match.score();
        TextSegment segment = match.embedded();
        String type = segment.metadata().getString(VectorMetadataKeys.TYPE);
        return "[" + type + " | 相关度:" + String.format("%.2f", score) + "] " + segment.text();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String preview(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
