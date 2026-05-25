package com.ai.rag.fulltext;

import com.ai.vector.ChunkMetadata;

/**
 * 全文检索命中结果。
 *
 * @param sourceType 来源类型
 * @param sourceId 来源文档编号
 * @param chunkId 分块编号
 * @param content 命中文本
 * @param score 全文检索相关度
 * @param metadata 分块元数据
 * @param parentContext 父级上下文
 * @author data-agent
 */
public record FullTextSearchResult(String sourceType,
        String sourceId,
        String chunkId,
        String content,
        double score,
        ChunkMetadata metadata,
        String parentContext) {

    public FullTextSearchResult(String sourceType, String sourceId, String chunkId, String content, double score) {
        this(sourceType, sourceId, chunkId, content, score, ChunkMetadata.plain(content), "");
    }

    public FullTextSearchResult(String sourceType, String sourceId, String chunkId, String content, double score,
            ChunkMetadata metadata) {
        this(sourceType, sourceId, chunkId, content, score, metadata, "");
    }

    public FullTextSearchResult(String sourceType, String sourceId, String chunkId, String content, double score,
            String sectionPath, int charStart, int charEnd, boolean containsTable, boolean containsCode,
            boolean containsList) {
        this(sourceType, sourceId, chunkId, content, score,
                new ChunkMetadata(sectionPath, charStart, charEnd, containsTable, containsCode, containsList), "");
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
}
