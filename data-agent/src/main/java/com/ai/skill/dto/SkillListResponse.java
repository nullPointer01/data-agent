package com.ai.skill.dto;

import java.util.List;

/**
 * Skill list response.
 *
 * @author data-agent
 */
public record SkillListResponse(boolean success, List<SkillResponse> skills) {
}
