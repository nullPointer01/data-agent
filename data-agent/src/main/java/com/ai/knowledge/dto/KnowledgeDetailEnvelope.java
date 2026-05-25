package com.ai.knowledge.dto;

/**
 * Envelope for knowledge detail response.
 *
 * @author data-agent
 */
public record KnowledgeDetailEnvelope(boolean success, String message, KnowledgeDetailResponse knowledge) {

    public static KnowledgeDetailEnvelope success(KnowledgeDetailResponse knowledge) {
        return new KnowledgeDetailEnvelope(true, null, knowledge);
    }

    public static KnowledgeDetailEnvelope failure(String message) {
        return new KnowledgeDetailEnvelope(false, message, null);
    }
}
