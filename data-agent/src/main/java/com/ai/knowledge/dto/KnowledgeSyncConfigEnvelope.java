package com.ai.knowledge.dto;

/**
 * Envelope for automatic synchronization config response.
 *
 * @author data-agent
 */
public record KnowledgeSyncConfigEnvelope(
        boolean success,
        String message,
        KnowledgeSyncConfigResponse syncConfig) {

    public static KnowledgeSyncConfigEnvelope success(KnowledgeSyncConfigResponse syncConfig) {
        return new KnowledgeSyncConfigEnvelope(true, null, syncConfig);
    }

    public static KnowledgeSyncConfigEnvelope failure(String message) {
        return new KnowledgeSyncConfigEnvelope(false, message, null);
    }
}
