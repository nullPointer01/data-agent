package com.ai.agent.dto;

/**
 * Agent 质量趋势点。
 *
 * @param date 日期
 * @param traceCount 轨迹数
 * @param feedbackCount 反馈数
 * @param successRate 成功率
 * @param positiveRate 正向反馈率
 * @param fallbackRate 回退率
 * @param averageDurationMs 平均耗时
 * @param qualityScore 质量分
 * @param positiveFeedbackCount 正向反馈数
 * @param negativeFeedbackCount 负向反馈数
 * @author data-agent
 */
public record AgentQualityTrendPointResponse(String date,
        long traceCount,
        long feedbackCount,
        double successRate,
        double positiveRate,
        double fallbackRate,
        long averageDurationMs,
        int qualityScore,
        long positiveFeedbackCount,
        long negativeFeedbackCount) {
}
