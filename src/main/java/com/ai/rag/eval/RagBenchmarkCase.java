package com.ai.rag.eval;

import java.util.List;
import java.util.Map;

/**
 * 一条人工标注的 RAG 检索评测用例。
 *
 * @param caseId 用例唯一编号
 * @param query 真实用户问题
 * @param expectedSourceIds 期望命中的来源编号
 * @param expectedChunkIds 期望命中的分块编号
 * @param relevanceGrades 分块相关等级，数值越大越相关
 * @param tags 用例分类标签
 * @author data-agent
 */
public record RagBenchmarkCase(
        String caseId,
        String query,
        List<String> expectedSourceIds,
        List<String> expectedChunkIds,
        Map<String, Integer> relevanceGrades,
        List<String> tags) {
}
