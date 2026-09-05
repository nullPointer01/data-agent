package com.ai.vector;

/**
 * 准备写入向量库的文本分块。
 *
 * @param id 分块编号
 * @param sourceId 来源文档编号
 * @param text 分块文本
 * @param metadata 分块元数据
 *
 * @author data-agent
 */
public record VectorChunk(String id,
        String sourceId,
        String text,
        ChunkMetadata metadata) {

    public VectorChunk(String id, String sourceId, String text) {
        this(id, sourceId, text, ChunkMetadata.plain(text));
    }

    public VectorChunk(String id, String sourceId, String text, String sectionPath, int charStart, int charEnd,
            boolean containsTable, boolean containsCode, boolean containsList) {
        this(id, sourceId, text,
                new ChunkMetadata(sectionPath, charStart, charEnd, containsTable, containsCode, containsList));
    }

    public String sectionPath() {
        return metadata.sectionPath();
    }

    public int charStart() {
        return metadata.charStart();
    }

    public int charEnd() {
        return metadata.charEnd();
    }

    public boolean containsTable() {
        return metadata.containsTable();
    }

    public boolean containsCode() {
        return metadata.containsCode();
    }

    public boolean containsList() {
        return metadata.containsList();
    }

    public String parentChunkId() {
        return metadata.parentChunkId();
    }

    public int parentCharStart() {
        return metadata.parentCharStart();
    }

    public int parentCharEnd() {
        return metadata.parentCharEnd();
    }
}
