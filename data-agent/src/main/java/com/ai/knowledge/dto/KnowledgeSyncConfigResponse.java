package com.ai.knowledge.dto;

import com.ai.model.KnowledgeSyncConfig;

import java.util.Date;

/**
 * Automatic synchronization config response.
 *
 * @author data-agent
 */
public record KnowledgeSyncConfigResponse(
        Long id,
        String knowledgeId,
        String syncType,
        String sourceUrl,
        String cronExpression,
        boolean enabled,
        Date lastSyncAt,
        String lastSyncStatus,
        Date createdAt) {

    public static KnowledgeSyncConfigResponse from(KnowledgeSyncConfig config) {
        return new KnowledgeSyncConfigResponse(
                config.getId(),
                config.getKnowledgeId(),
                config.getSyncType(),
                config.getSourceUrl(),
                config.getCronExpression(),
                config.isEnabled(),
                config.getLastSyncAt(),
                config.getLastSyncStatus(),
                config.getCreatedAt());
    }
}
