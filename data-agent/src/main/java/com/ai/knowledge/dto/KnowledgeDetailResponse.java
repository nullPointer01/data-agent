package com.ai.knowledge.dto;

import com.ai.model.KnowledgeEntry;

import java.util.Date;

/**
 * Knowledge detail response.
 *
 * @author data-agent
 */
public record KnowledgeDetailResponse(
        String knowledgeId,
        String name,
        String description,
        String sourceType,
        String sourceFilename,
        String content,
        int chunkCount,
        long contentLength,
        Date createdAt,
        Date updatedAt) {

    public static KnowledgeDetailResponse from(KnowledgeEntry entry) {
        return new KnowledgeDetailResponse(
                entry.getKnowledgeId(),
                entry.getName(),
                entry.getDescription(),
                entry.getSourceType(),
                entry.getSourceFilename(),
                entry.getContent(),
                entry.getChunkCount(),
                entry.getContentLength(),
                entry.getCreatedAt(),
                entry.getUpdatedAt());
    }
}
