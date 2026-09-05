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
        String type,
        String description,
        String systemPrompt,
        String modelId,
        String skillId,
        String datasourceId,
        String executionMode,
        List<String> tools,
        boolean enabled,
        String tenantId,
        String createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static AgentProfileResponse from(AgentProfile profile) {
        return new AgentProfileResponse(
                profile.getAgentId(),
                profile.getName(),
                profile.getTypeCode(),
                profile.getDescription(),
                profile.getSystemPrompt(),
                profile.getModelId(),
                profile.getSkillId(),
                profile.getDatasourceId(),
                profile.getExecutionMode(),
                profile.getToolList(),
                profile.isEnabled(),
                profile.getTenantId(),
                profile.getCreatedBy(),
                profile.getCreatedAt(),
                profile.getUpdatedAt());
    }
}
