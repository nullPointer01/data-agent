package com.ai.skill.dto;

/**
 * Request payload for creating or updating a skill.
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
        String responseTemplate,
        String keywords,
        String steps,
        String autoAttach,
        String source,
        Boolean enabled,
        String remark) {
}
