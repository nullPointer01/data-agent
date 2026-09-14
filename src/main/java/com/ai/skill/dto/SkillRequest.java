package com.ai.skill.dto;

/**
 * 创建或更新技能的请求负载。
 *
 * @author data-agent
 */
public record SkillRequest(
        String name,
        String description,
        String version,
        String apiUrl,
        String apiMethod,
        String apiHeaders,
        String promptTemplate,
        String steps,
        String source,
        Boolean enabled,
        String remark) {
}
