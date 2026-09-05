package com.ai.agent.dto;

/**
 * Agent 质量风险项。
 *
 * @param riskCode 风险编码
 * @param riskName 风险名称
 * @param severity 风险级别
 * @param message 风险说明
 * @param evidence 证据摘要
 * @author data-agent
 */
public record AgentQualityRiskResponse(String riskCode,
        String riskName,
        String severity,
        String message,
        String evidence) {
}
