package com.ai.skill.dto;

/**
 * 创建或更新技能指令的请求负载。
 * apiUrl/apiMethod/apiHeaders 仅用于识别旧客户端合同；API 地址/凭据只允许清空，执行改用 Tool/MCP。
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
