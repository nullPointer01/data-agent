package com.ai.rag.dto;

import java.util.List;

/**
 * RAG 能力健康和验收状态。
 *
 * @param enabled RAG 是否启用
 * @param healthy 整体是否健康
 * @param fullTextProvider 全文检索提供方
 * @param fullTextHealthy 全文检索是否健康
 * @param vectorProvider 向量检索提供方
 * @param vectorHealthy 向量检索是否健康
 * @param hybridRetrievalReady 混合检索是否就绪
 * @param rerankerReady 重排器是否就绪
 * @param citationReady 引用溯源是否就绪
 * @param esLatencyThresholdMs ES 延迟阈值
 * @param milvusLatencyThresholdMs Milvus 延迟阈值
 * @param ragLatencyThresholdMs RAG 端到端延迟阈值
 * @param lastTrace 最近一次 RAG 检索链路信息
 * @param acceptanceChecks 验收项状态
 * @param issues 健康问题
 * @author data-agent
 */
public record RagHealthResponse(
        boolean enabled,
        boolean healthy,
        String fullTextProvider,
        boolean fullTextHealthy,
        String vectorProvider,
        boolean vectorHealthy,
        boolean hybridRetrievalReady,
        boolean rerankerReady,
        boolean citationReady,
        long esLatencyThresholdMs,
        long milvusLatencyThresholdMs,
        long ragLatencyThresholdMs,
        RagRetrievalTrace lastTrace,
        List<RagAcceptanceCheck> acceptanceChecks,
        List<String> issues) {
}
