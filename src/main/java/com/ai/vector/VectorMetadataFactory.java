package com.ai.vector;

import dev.langchain4j.data.document.Metadata;

/**
 * 构建向量片段元数据。
 *
 * @author data-agent
 */
public final class VectorMetadataFactory {

    private VectorMetadataFactory() {
    }

    public static Metadata create(String type, String id, String sourceId, String tenantId, String userId,
            EmbeddingProfile profile) {
        return new Metadata()
                .put(VectorMetadataKeys.TYPE, type)
                .put(VectorMetadataKeys.ID, id)
                .put(VectorMetadataKeys.SOURCE_ID, sourceId)
                .put(VectorMetadataKeys.TENANT_ID, tenantId)
                .put(VectorMetadataKeys.USER_ID, userId)
                .put(VectorMetadataKeys.EMBEDDING_PROVIDER, profile.provider())
                .put(VectorMetadataKeys.EMBEDDING_MODEL_ID, profile.modelId())
                .put(VectorMetadataKeys.EMBEDDING_INDEX_VERSION, profile.indexVersion())
                .put(VectorMetadataKeys.EMBEDDING_DIMENSION, String.valueOf(profile.dimension()))
                .put(VectorMetadataKeys.EMBEDDING_METRIC, profile.metric());
    }

    public static Metadata create(String type, VectorChunk chunk, String tenantId, String userId,
            EmbeddingProfile profile) {
        return create(type, chunk.id(), chunk.sourceId(), tenantId, userId, profile)
                .put(VectorMetadataKeys.SECTION_PATH, chunk.sectionPath())
                .put(VectorMetadataKeys.CHAR_START, String.valueOf(chunk.charStart()))
                .put(VectorMetadataKeys.CHAR_END, String.valueOf(chunk.charEnd()))
                .put(VectorMetadataKeys.CONTAINS_TABLE, String.valueOf(chunk.containsTable()))
                .put(VectorMetadataKeys.CONTAINS_CODE, String.valueOf(chunk.containsCode()))
                .put(VectorMetadataKeys.CONTAINS_LIST, String.valueOf(chunk.containsList()))
                .put(VectorMetadataKeys.PARENT_CHUNK_ID, chunk.parentChunkId())
                .put(VectorMetadataKeys.PARENT_CHAR_START, String.valueOf(chunk.parentCharStart()))
                .put(VectorMetadataKeys.PARENT_CHAR_END, String.valueOf(chunk.parentCharEnd()));
    }
}
