package com.ai.knowledge.dto;

/**
 * 知识自动同步配置请求。
 *
 * @author data-agent
 */
public record KnowledgeSyncConfigRequest(
        String syncType,
        String sourceUrl,
        String cronExpression,
        Boolean enabled) {
}
