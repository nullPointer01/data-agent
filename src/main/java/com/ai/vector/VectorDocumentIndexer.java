package com.ai.vector;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 将文本向量化并写入 Milvus。
 *
 * @author data-agent
 */
@Component
public class VectorDocumentIndexer {

    private final EmbeddingGateway embeddingGateway;

    private final MilvusVectorStoreGateway vectorStoreGateway;

    private final VectorIndexRegistry indexRegistry;

    private final int batchSize;

    public VectorDocumentIndexer(EmbeddingGateway embeddingGateway, MilvusVectorStoreGateway vectorStoreGateway,
            VectorIndexRegistry indexRegistry, EmbeddingProperties properties) {
        this.embeddingGateway = embeddingGateway;
        this.vectorStoreGateway = vectorStoreGateway;
        this.indexRegistry = indexRegistry;
        this.batchSize = properties.getApi().getBatchSize();
    }

    public void index(String text, String type, String id, String sourceId, String tenantId, String userId) {
        TextSegment segment = TextSegment.from(text, VectorMetadataFactory.create(type, id, sourceId, tenantId,
                userId, embeddingGateway.getProfile()));
        Embedding embedding = embeddingGateway.embedDocument(segment);
        String primaryKey = vectorStoreGateway.add(embedding, segment);
        indexRegistry.record(type, id, text, primaryKey, sourceId, tenantId, userId);
    }

    public void index(String text, String type, VectorChunk chunk, String tenantId, String userId) {
        TextSegment segment = TextSegment.from(text, VectorMetadataFactory.create(type, chunk, tenantId, userId,
                embeddingGateway.getProfile()));
        Embedding embedding = embeddingGateway.embedDocument(segment);
        String primaryKey = vectorStoreGateway.add(embedding, segment);
        indexRegistry.record(type, chunk.id(), text, primaryKey, chunk.sourceId(), tenantId, userId);
    }

    /**
     * 将结构化文档分块按配置大小串行向量化并批量写入 Milvus。
     *
     * @param chunks 保持原始顺序的文档分块
     * @param type 向量文档类型
     * @param textPrefix 每个分块写入前追加的固定业务标签，可为空
     * @param tenantId 租户编号
     * @param userId 用户编号
     */
    public void indexAll(List<VectorChunk> chunks, String type, String textPrefix,
            String tenantId, String userId) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        String prefix = textPrefix == null ? "" : textPrefix;
        List<TextSegment> segments = chunks.stream()
                .map(chunk -> TextSegment.from(prefix + chunk.text(),
                        VectorMetadataFactory.create(type, chunk, tenantId, userId,
                                embeddingGateway.getProfile())))
                .toList();

        for (int start = 0; start < segments.size(); start += batchSize) {
            int end = Math.min(start + batchSize, segments.size());
            List<TextSegment> batch = List.copyOf(segments.subList(start, end));
            List<Embedding> embeddings = embeddingGateway.embedDocuments(batch);
            List<String> primaryKeys = vectorStoreGateway.addAll(embeddings, batch);
            indexRegistry.recordAll(batch, primaryKeys);
        }
    }
}
