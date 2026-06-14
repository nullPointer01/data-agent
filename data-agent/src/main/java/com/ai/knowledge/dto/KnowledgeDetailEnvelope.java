package com.ai.knowledge.dto;

/**
 * 知识详情响应信封。
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
