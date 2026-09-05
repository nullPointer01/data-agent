package com.ai.skill.dto;

import java.util.List;

/**
 * 技能列表响应。
 *
 * @author data-agent
 */
public record SkillListResponse(boolean success, List<SkillResponse> skills) {
}
