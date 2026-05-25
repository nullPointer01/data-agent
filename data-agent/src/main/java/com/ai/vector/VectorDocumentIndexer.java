package com.ai.vector;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import org.springframework.stereotype.Component;

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

    public VectorDocumentIndexer(EmbeddingGateway embeddingGateway, MilvusVectorStoreGateway vectorStoreGateway,
            VectorIndexRegistry indexRegistry) {
        this.embeddingGateway = embeddingGateway;
        this.vectorStoreGateway = vectorStoreGateway;
        this.indexRegistry = indexRegistry;
    }

    public void index(String text, String type, String id, String sourceId, String tenantId, String userId) {
        TextSegment segment = TextSegment.from(text, VectorMetadataFactory.create(type, id, sourceId, tenantId, userId));
        Embedding embedding = embeddingGateway.embed(segment);
        String primaryKey = vectorStoreGateway.add(embedding, segment);
        indexRegistry.record(type, id, text, primaryKey, sourceId, tenantId, userId);
    }

    public void index(String text, String type, VectorChunk chunk, String tenantId, String userId) {
        TextSegment segment = TextSegment.from(text, VectorMetadataFactory.create(type, chunk, tenantId, userId));
        Embedding embedding = embeddingGateway.embed(segment);
        String primaryKey = vectorStoreGateway.add(embedding, segment);
        indexRegistry.record(type, chunk.id(), text, primaryKey, chunk.sourceId(), tenantId, userId);
    }
}
