package com.ai.agent.dto;

/**
 * Agent 质量分组统计。
 *
 * @param agentKey Agent 分组键
 * @param agentName Agent 名称
 * @param agentType Agent 类型
 * @param traceCount 执行轨迹数
 * @param successRate 成功率
 * @param fallbackRate 回退率
 * @param averageDurationMs 平均耗时
 * @param feedbackCount 反馈数
 * @param negativeFeedbackCount 负向反馈数
 * @param positiveRate 正向反馈率
 * @param qualityScore 质量分
 * @author data-agent
 */
public record AgentQualityAgentStatResponse(String agentKey,
        String agentName,
        String agentType,
        long traceCount,
        double successRate,
        double fallbackRate,
        long averageDurationMs,
        long feedbackCount,
        long negativeFeedbackCount,
        double positiveRate,
        int qualityScore) {
}
