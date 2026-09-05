package com.ai.agent.dto;

/**
 * Agent 质量问题归因响应。
 *
 * @param causeCode 归因编码
 * @param causeName 归因名称
 * @param severity 风险级别
 * @param count 命中数量
 * @param ratio 命中占比
 * @param sampleTraceId 样本轨迹编号
 * @param sampleQuestion 样本问题
 * @param recommendation 处理建议
 * @author data-agent
 */
public record AgentQualityAttributionResponse(String causeCode,
        String causeName,
        String severity,
        long count,
        double ratio,
        String sampleTraceId,
        String sampleQuestion,
        String recommendation) {
}
