package com.ai.skill.dto;

import com.ai.model.SkillPromptHistory;

import java.util.Date;

/**
 * Skill prompt history response.
 *
 * @author data-agent
 */
public record SkillHistoryResponse(
        Long id,
        String skillId,
        int version,
        String promptTemplate,
        String steps,
        String remark,
        String tenantId,
        Date createdAt) {

    public static SkillHistoryResponse from(SkillPromptHistory history) {
        return new SkillHistoryResponse(
                history.getId(),
                history.getSkillId(),
                history.getVersion(),
                history.getPromptTemplate(),
                history.getSteps(),
                history.getRemark(),
                history.getTenantId(),
                history.getCreatedAt());
    }
}
