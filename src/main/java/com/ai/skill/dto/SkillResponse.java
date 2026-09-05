package com.ai.skill.dto;

import com.ai.model.SkillConfig;

import java.time.LocalDateTime;

/**
 * 技能配置响应。
 *
 * @author data-agent
 */
public record SkillResponse(
        String skillId,
        String name,
        String description,
        String version,
        String apiUrl,
        String apiMethod,
        String apiHeaders,
        String promptTemplate,
        String responseTemplate,
        String keywords,
        String steps,
        String autoAttach,
        String source,
        int feedbackCount,
        int positiveCount,
        boolean enabled,
        boolean isDefault,
        String tenantId,
        String createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static SkillResponse from(SkillConfig config) {
        return new SkillResponse(
                config.getSkillId(),
                config.getName(),
                config.getDescription(),
                config.getVersion(),
                config.getApiUrl(),
                config.getApiMethod(),
                config.getApiHeaders(),
                config.getPromptTemplate(),
                config.getResponseTemplate(),
                config.getKeywords(),
                config.getSteps(),
                config.getAutoAttach(),
                config.getSource(),
                config.getFeedbackCount() == null ? 0 : config.getFeedbackCount(),
                config.getPositiveCount() == null ? 0 : config.getPositiveCount(),
                config.isEnabled(),
                config.isDefault(),
                config.getTenantId(),
                config.getCreatedBy(),
                config.getCreatedAt(),
                config.getUpdatedAt());
    }
}
