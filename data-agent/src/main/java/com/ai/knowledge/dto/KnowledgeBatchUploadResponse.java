package com.ai.knowledge.dto;

import java.util.List;

/**
 * Response for batch knowledge upload.
 *
 * @author data-agent
 */
public record KnowledgeBatchUploadResponse(
        boolean success,
        int total,
        int successCount,
        int failedCount,
        List<KnowledgeBatchItemResponse> results) {
}
