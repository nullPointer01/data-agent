package com.ai.rag.retrieval;
import com.ai.rag.retrieval.RetrievalChannel;
import com.ai.rag.fulltext.FullTextSearchResult;

import com.ai.vector.ChunkMetadata;

import java.util.List;

/**
 * 内部统一检索结果。
 *
 * @param sourceType 来源类型
 * @param sourceId 来源文档编号
 * @param chunkId 分块编号
 * @param content 命中文本
 * @param score 归一化相关度
 * @param vectorScore 向量检索原始分数
 * @param fullTextScore 全文检索原始分数
 * @param channels 命中的检索通道
 * @param metadata 分块元数据
 * @param parentContext 父级上下文
 * @author data-agent
 */
public record RetrievalResult(String sourceType,
        String sourceId,
        String chunkId,
        String content,
        double score,
        Double vectorScore,
        Double fullTextScore,
        List<String> channels,
        ChunkMetadata metadata,
        String parentContext) {

    public RetrievalResult(String sourceType, String sourceId, String chunkId, String content, double score,
            Double vectorScore, Double fullTextScore, List<String> channels) {
        this(sourceType, sourceId, chunkId, content, score, vectorScore, fullTextScore, channels,
                ChunkMetadata.plain(content), "");
    }

    public RetrievalResult(String sourceType, String sourceId, String chunkId, String content, double score,
            Double vectorScore, Double fullTextScore, List<String> channels, ChunkMetadata metadata) {
        this(sourceType, sourceId, chunkId, content, score, vectorScore, fullTextScore, channels, metadata, "");
    }

    /**
     * 从全文检索候选创建统一结果。
     *
     * @param result 全文检索结果
     * @return 统一检索结果
     */
    public static RetrievalResult fromFullText(FullTextSearchResult result) {
        return new RetrievalResult(result.sourceType(), result.sourceId(), result.chunkId(), result.content(),
                result.score(), null, result.score(), List.of(RetrievalChannel.FULL_TEXT), result.metadata(),
                result.parentContext());
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

    /**
     * 判断是否来自向量检索。
     *
     * @return 是否命中向量通道
     */
    public boolean hasVectorScore() {
        return vectorScore != null;
    }

    /**
     * 判断是否来自全文检索。
     *
     * @return 是否命中全文通道
     */
    public boolean hasFullTextScore() {
        return fullTextScore != null;
    }
}
