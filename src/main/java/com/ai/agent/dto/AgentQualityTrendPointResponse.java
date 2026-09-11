package com.ai.agent.dto;

/**
 * Agent 质量趋势点。
 *
 * @param date 日期
 * @param traceCount 轨迹数
 * @param successRate 成功率
 * @param fallbackRate 回退率
 * @param averageDurationMs 平均耗时
 * @param qualityScore 质量分
 * @author data-agent
 */
public record AgentQualityTrendPointResponse(String date,
        long traceCount,
        double successRate,
        double fallbackRate,
        long averageDurationMs,
        int qualityScore) {
}
