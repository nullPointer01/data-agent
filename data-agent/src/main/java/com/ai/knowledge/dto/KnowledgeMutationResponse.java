package com.ai.knowledge.dto;

/**
 * Response for knowledge create, update, delete and sync commands.
 *
 * @author data-agent
 */
public record KnowledgeMutationResponse(
        boolean success,
        String message,
        String knowledgeId,
        String name,
        Integer chunkCount,
        Long contentLength) {

    public static KnowledgeMutationResponse success(String message) {
        return new KnowledgeMutationResponse(true, message, null, null, null, null);
    }

    public static KnowledgeMutationResponse success(String knowledgeId, String name, int chunkCount,
            long contentLength, String message) {
        return new KnowledgeMutationResponse(true, message, knowledgeId, name, chunkCount, contentLength);
    }

    public static KnowledgeMutationResponse success(String knowledgeId, String name, int chunkCount, String message) {
        return new KnowledgeMutationResponse(true, message, knowledgeId, name, chunkCount, null);
    }

    public static KnowledgeMutationResponse failure(String message) {
        return new KnowledgeMutationResponse(false, message, null, null, null, null);
    }
}
