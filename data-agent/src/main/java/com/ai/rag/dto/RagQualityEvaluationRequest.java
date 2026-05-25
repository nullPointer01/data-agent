package com.ai.rag.dto;

import java.util.List;

/**
 * RAG 质量评估请求。
 *
 * @param query 原始查询
 * @param response 待评估的 RAG 检索响应
 * @param expectedKeywords 业务期望命中的关键词
 * @param expectedSourceIds 业务期望命中的来源编号
 * @author data-agent
 */
public record RagQualityEvaluationRequest(
        String query,
        RagContextResponse response,
        List<String> expectedKeywords,
        List<String> expectedSourceIds) {
}
