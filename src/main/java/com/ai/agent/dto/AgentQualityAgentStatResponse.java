package com.ai.agent.dto;

/**
 * Agent 质量分组统计。
 *
 * @param agentKey Agent 分组键
 * @param agentName Agent 名称
 * @param agentType 运行模式；字段名为历史兼容名称
 * @param traceCount 执行轨迹数
 * @param successRate 成功率
 * @param fallbackRate 回退率
 * @param averageDurationMs 平均耗时
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
        int qualityScore) {
}
