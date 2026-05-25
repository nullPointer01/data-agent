package com.ai.knowledge.dto;

import com.ai.model.KnowledgeEntry;

import java.util.Date;

/**
 * Knowledge list item response.
 *
 * @author data-agent
 */
public record KnowledgeItemResponse(
        String knowledgeId,
        String name,
        String description,
        String sourceType,
        String sourceFilename,
        int chunkCount,
        long contentLength,
        Date createdAt,
        Date updatedAt) {

    public static KnowledgeItemResponse from(KnowledgeEntry entry) {
        return new KnowledgeItemResponse(
                entry.getKnowledgeId(),
                entry.getName(),
                entry.getDescription(),
                entry.getSourceType(),
                entry.getSourceFilename(),
                entry.getChunkCount(),
                entry.getContentLength(),
                entry.getCreatedAt(),
                entry.getUpdatedAt());
    }
}
