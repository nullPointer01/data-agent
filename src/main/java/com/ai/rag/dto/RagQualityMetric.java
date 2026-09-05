package com.ai.rag.dto;

/**
 * RAG 质量评估指标。
 *
 * @param key 指标编码
 * @param name 指标名称
 * @param score 指标得分，取值范围为 0 到 1
 * @param threshold 通过阈值
 * @param passed 是否通过
 * @param detail 评估说明
 * @author data-agent
 */
public record RagQualityMetric(
        String key,
        String name,
        double score,
        double threshold,
        boolean passed,
        String detail) {
}
