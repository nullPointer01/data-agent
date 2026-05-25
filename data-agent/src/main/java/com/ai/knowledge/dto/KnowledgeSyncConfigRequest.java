package com.ai.knowledge.dto;

/**
 * Request for automatic knowledge synchronization config.
 *
 * @author data-agent
 */
public record KnowledgeSyncConfigRequest(
        String syncType,
        String sourceUrl,
        String cronExpression,
        Boolean enabled) {
}
