package com.ai.agent.dto;

import java.util.List;

/**
 * Agent 质量看板响应。
 *
 * @param success 是否成功
 * @param sampleSize 轨迹样本数
 * @param qualityScore 综合质量分
 * @param qualityLevel 质量等级
 * @param successRate 执行成功率
 * @param averageDurationMs 平均耗时
 * @param fallbackRate 回退率
 * @param attributions 质量问题归因
 * @param risks 风险列表
 * @param recommendations 优化建议
 * @param agentStats Agent 分组统计
 * @param trendPoints 趋势点
 * @author data-agent
 */
public record AgentQualityDashboardResponse(boolean success,
        int sampleSize,
        int qualityScore,
        String qualityLevel,
        double successRate,
        long averageDurationMs,
        double fallbackRate,
        List<AgentQualityAttributionResponse> attributions,
        List<AgentQualityRiskResponse> risks,
        List<String> recommendations,
        List<AgentQualityAgentStatResponse> agentStats,
        List<AgentQualityTrendPointResponse> trendPoints) {
}
