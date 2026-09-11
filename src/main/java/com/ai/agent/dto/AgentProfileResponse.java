package com.ai.agent.dto;

import com.ai.model.AgentProfile;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent 配置响应。
 *
 * @author data-agent
 */
public record AgentProfileResponse(
        String agentId,
        String name,
        String description,
        String systemPrompt,
        String modelId,
        String executionMode,
        List<String> capabilityBindings,
        boolean defaultAgent,
        boolean enabled,
        String tenantId,
        String createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static AgentProfileResponse from(
            AgentProfile profile,
            List<String> capabilityBindings) {
        return new AgentProfileResponse(
                profile.getAgentId(),
                profile.getName(),
                profile.getDescription(),
                profile.getSystemPrompt(),
                profile.getModelId(),
                profile.getExecutionMode(),
                capabilityBindings == null ? List.of() : List.copyOf(capabilityBindings),
                profile.isDefaultAgent(),
                profile.isEnabled(),
                profile.getTenantId(),
                profile.getCreatedBy(),
                profile.getCreatedAt(),
                profile.getUpdatedAt());
    }
}
