package com.ai.knowledge.dto;

import java.util.List;

/**
 * 知识检索命中结果。
 *
 * @param referenceId 引用编号
 * @param sourceType 来源类型
 * @param sourceId 来源文档编号
 * @param chunkId 向量片段编号
 * @param score 检索分数
 * @param vectorScore 向量检索原始分数
 * @param fullTextScore 全文检索原始分数
 * @param channels 命中的检索通道
 * @param content 命中文本
 * @param sectionPath 标题路径
 * @param charStart 起始字符位置
 * @param charEnd 结束字符位置
 * @param containsTable 是否包含表格
 * @param containsCode 是否包含代码块
 * @param containsList 是否包含列表
 * @author data-agent
 */
public record KnowledgeSearchResult(
        String referenceId,
        String sourceType,
        String sourceId,
        String chunkId,
        double score,
        Double vectorScore,
        Double fullTextScore,
        List<String> channels,
        String content,
        String sectionPath,
        int charStart,
        int charEnd,
        boolean containsTable,
        boolean containsCode,
        boolean containsList) {
}
