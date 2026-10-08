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
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
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
    private static final String USER_LABEL = "用户: ";
    private static final String ASSISTANT_LABEL = "\n助手: ";
    private static final String FILE_LABEL_PREFIX = "文件[";
    private static final String FILE_LABEL_SUFFIX = "] ";
    private static final String MEMORY_LABEL_PREFIX = "记忆: ";

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


    /** 按 Run 身份重放一轮对话索引；异常必须向持久任务传播，不能记为成功。 */
    public void indexConversationTurn(String runId, String question, String answer, String tenantId, String userId) {
        VectorIdentity identity = identityNormalizer.normalize(tenantId, userId);
        requireVectorStoreAvailable(VectorDocumentTypes.CONVERSATION, runId);
        removeFromStore(VectorDocumentTypes.CONVERSATION, runId, identity.tenantId());
        vectorDocumentIndexer.index(USER_LABEL + question + ASSISTANT_LABEL + answer,
                VectorDocumentTypes.CONVERSATION, runId, runId, identity.tenantId(),identity.userId());
    }

    /** 接收同一次分块结果；空列表也删除原记录，不能让向量侧再计算另一套边界。 */
    public void replaceDocumentChunks(String type, String source, String title, List<VectorChunk> chunks,
            String tenantId, String userId) {
        if ((!VectorDocumentTypes.FILE.equals(type) && !VectorDocumentTypes.KNOWLEDGE.equals(type))
                || isBlank(source) || isBlank(tenantId) || isBlank(userId)
                || chunks == null || chunks.stream().anyMatch(chunk -> !source.equals(chunk.sourceId()))) {
            throw new IllegalArgumentException("向量文档分块与来源身份不一致");
        }
        VectorIdentity identity = identityNormalizer.normalize(tenantId, userId);
        requireVectorStoreAvailable(type, source);
        removeFromStore(type, source, identity.tenantId());
        String prefix = VectorDocumentTypes.FILE.equals(type) ? FILE_LABEL_PREFIX + title + FILE_LABEL_SUFFIX : "";
        vectorDocumentIndexer.indexAll(chunks, type, prefix,
                identity.tenantId(), identity.userId());
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
        if (isBlank(content)) {
            return;
        }
        VectorIdentity identity = identityNormalizer.normalize(tenantId, userId);
        requireVectorStoreAvailable(VectorDocumentTypes.MEMORY, memoryId);
        removeFromStore(VectorDocumentTypes.MEMORY, memoryId, identity.tenantId());

        List<VectorChunk> chunks = textChunker.split(memoryId, content);
        // 重建和正常入库共用批量入口，失败必须交给调用方降级或持久任务重试。
        vectorDocumentIndexer.indexAll(chunks, VectorDocumentTypes.MEMORY, MEMORY_LABEL_PREFIX,
                identity.tenantId(), identity.userId());
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
            LOGGER.debug("向量检索未找到相关结果");
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
            LOGGER.debug("向量检索未找到相关结果");
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
        if (isBlank(query)) {
            return List.of();
        }
        // 官方 Retriever 负责查询向量化、构建检索请求和返回 Content；项目只绑定身份和转换旧响应合同。
        var retriever = EmbeddingStoreContentRetriever.builder()
                .embeddingModel(embeddingGateway)
                .embeddingStore(vectorStoreGateway)
                .maxResults(topK).minScore(minScore)
                .filter(VectorMetadataFilters.searchFilter(tenantId, userId, sourceTypes))
                .build();
        return retriever.retrieve(Query.from(query)).stream().map(content -> new EmbeddingMatch<TextSegment>(
                ((Number) content.metadata().get(ContentMetadata.SCORE)).doubleValue(),
                (String) content.metadata().get(ContentMetadata.EMBEDDING_ID), null, content.textSegment())).toList();
    }

    public synchronized void removeFromStore(String type, String id) {
        removeFromStore(type, id, null);
    }

    public synchronized void removeFromStore(String type, String id, String tenantId) {
        if (!vectorStoreGateway.deleteBySource(type, id, tenantId)) {
            throw new IllegalStateException("向量删除未成功，保留任务以供重试");
        }
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

    private void requireVectorStoreAvailable(String type, String sourceId) {
        if (!vectorStoreGateway.isAvailable()) {
            throw new IllegalStateException("向量存储不可用，无法完成文档索引: type=" + type
                    + ", sourceId=" + sourceId);
        }
    }

    private String formatMatch(EmbeddingMatch<TextSegment> match) {
        double score = match.score();
        TextSegment segment = match.embedded();
        String type = segment.metadata().getString(VectorMetadataKeys.TYPE);
        return "[" + type + " | 相关度:" + String.format("%.2f", score) + "] " + segment.text();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

}
