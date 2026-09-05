package com.ai.skill.dto;

import java.util.List;

/**
 * 技能提示历史列表响应。
 *
 * @author data-agent
 */
public record SkillHistoryListResponse(boolean success, List<SkillHistoryResponse> history) {
}
