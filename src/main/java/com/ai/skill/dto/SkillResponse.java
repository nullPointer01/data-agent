package com.ai.skill.dto;

import com.ai.model.SkillConfig;
import com.ai.skill.SkillCatalog;

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
        String configurationIssue,
        String promptTemplate,
        String steps,
        String source,
        boolean enabled,
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
                config.getApiUrl() == null ? "" : config.getApiUrl(),
                SkillCatalog.configurationIssue(config),
                config.getPromptTemplate(),
                config.getSteps(),
                config.getSource(),
                config.isEnabled(),
                config.getTenantId(),
                config.getCreatedBy(),
                config.getCreatedAt(),
                config.getUpdatedAt());
    }
}
