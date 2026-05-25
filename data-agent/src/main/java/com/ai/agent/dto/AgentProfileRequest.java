package com.ai.agent.dto;

/**
 * Agent 配置创建和更新请求。
 *
 * @author data-agent
 */
public record AgentProfileRequest(
        String agentId,
        String name,
        String type,
        String description,
        String systemPrompt,
        String modelId,
        String skillId,
        String datasourceId,
        Boolean enabled) {
}
