package com.ai.rag;

import java.util.List;

/**
 * RAG 查询分析结果。
 *
 * @param originalQuery 原始查询
 * @param rewrittenQuery 改写后的主查询
 * @param keywords 关键词
 * @param queryType 查询类型
 * @param variants 检索变体
 * @author data-agent
 */
public record RagQueryAnalysis(String originalQuery,
        String rewrittenQuery,
        List<String> keywords,
        String queryType,
        List<String> variants) {
}
