package com.ai.rag.dto;

import java.util.List;

/**
 * RAG 检索引用来源。
 *
 * @param referenceId 引用编号
 * @param sourceType 来源类型
 * @param sourceId 来源编号
 * @param chunkId 片段编号
 * @param score 检索分数
 * @param vectorScore 向量检索原始分数
 * @param fullTextScore 全文检索原始分数
 * @param channels 命中的检索通道
 * @param snippet 片段摘要
 * @param sectionPath 标题路径
 * @param charStart 起始字符位置
 * @param charEnd 结束字符位置
 * @param containsTable 是否包含表格
 * @param containsCode 是否包含代码块
 * @param containsList 是否包含列表
 * @param parentContextUsed 是否使用父级上下文
 * @author data-agent
 */
public record RagCitation(
        String referenceId,
        String sourceType,
        String sourceId,
        String chunkId,
        double score,
        Double vectorScore,
        Double fullTextScore,
        List<String> channels,
        String snippet,
        String sectionPath,
        int charStart,
        int charEnd,
        boolean containsTable,
        boolean containsCode,
        boolean containsList,
        boolean parentContextUsed) {

    public RagCitation(String referenceId, String sourceType, String sourceId, String chunkId, double score,
            String snippet) {
        this(referenceId, sourceType, sourceId, chunkId, score, null, null, List.of(), snippet, "", 0, 0,
                false, false, false, false);
    }

    public RagCitation(String referenceId, String sourceType, String sourceId, String chunkId, double score,
            String snippet, String sectionPath, int charStart, int charEnd) {
        this(referenceId, sourceType, sourceId, chunkId, score, null, null, List.of(), snippet, sectionPath,
                charStart, charEnd, false, false, false, false);
    }
}
