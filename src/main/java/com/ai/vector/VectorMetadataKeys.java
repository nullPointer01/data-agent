package com.ai.vector;

/**
 * 向量片段元数据键。
 *
 * @author data-agent
 */
public final class VectorMetadataKeys {

    public static final String TYPE = "type";
    public static final String ID = "id";
    public static final String SOURCE_ID = "sourceId";
    public static final String TENANT_ID = "tenantId";
    public static final String USER_ID = "userId";
    public static final String SECTION_PATH = "sectionPath";
    public static final String CHAR_START = "charStart";
    public static final String CHAR_END = "charEnd";
    public static final String CONTAINS_TABLE = "containsTable";
    public static final String CONTAINS_CODE = "containsCode";
    public static final String CONTAINS_LIST = "containsList";
    public static final String PARENT_CHUNK_ID = "parentChunkId";
    public static final String PARENT_CHAR_START = "parentCharStart";
    public static final String PARENT_CHAR_END = "parentCharEnd";
    public static final String EMBEDDING_PROVIDER = "embeddingProvider";
    public static final String EMBEDDING_MODEL_ID = "embeddingModelId";
    public static final String EMBEDDING_INDEX_VERSION = "embeddingIndexVersion";
    public static final String EMBEDDING_DIMENSION = "embeddingDimension";
    public static final String EMBEDDING_METRIC = "embeddingMetric";

    private VectorMetadataKeys() {
    }
}
