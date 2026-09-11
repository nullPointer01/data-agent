package com.ai.agent.dto;

import java.util.List;

/**
 * Agent 配置创建和更新请求。
 *
 * @author data-agent
 */
public record AgentProfileRequest(
        String agentId,
        String name,
        String description,
        String systemPrompt,
        String modelId,
        String executionMode,
        List<String> capabilityBindings,
        Boolean enabled) {
}
