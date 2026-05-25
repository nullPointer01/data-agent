package com.ai.rag.dto;

import java.util.List;

/**
 * RAG 质量评估结果。
 *
 * @param passed 是否通过质量门禁
 * @param overallScore 综合得分，取值范围为 0 到 1
 * @param relevanceScore 相关性得分
 * @param citationAccuracy 引用准确率或完整性得分
 * @param hybridCoverage 混合召回覆盖得分
 * @param latencyScore 延迟得分
 * @param sourceCoverage 期望来源覆盖得分
 * @param expectedKeywordCount 期望关键词数量
 * @param matchedKeywordCount 命中关键词数量
 * @param citationCount 引用数量
 * @param validCitationCount 有效引用数量
 * @param matchedKeywords 已命中的关键词
 * @param metrics 指标明细
 * @param issues 质量问题
 * @param recommendations 优化建议
 * @author data-agent
 */
public record RagQualityEvaluationResponse(
        boolean passed,
        double overallScore,
        double relevanceScore,
        double citationAccuracy,
        double hybridCoverage,
        double latencyScore,
        double sourceCoverage,
        int expectedKeywordCount,
        int matchedKeywordCount,
        int citationCount,
        int validCitationCount,
        List<String> matchedKeywords,
        List<RagQualityMetric> metrics,
        List<String> issues,
        List<String> recommendations) {
}
