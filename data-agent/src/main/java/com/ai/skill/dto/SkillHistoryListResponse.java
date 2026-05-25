package com.ai.skill.dto;

import java.util.List;

/**
 * Skill prompt history list response.
 *
 * @author data-agent
 */
public record SkillHistoryListResponse(boolean success, List<SkillHistoryResponse> history) {
}
