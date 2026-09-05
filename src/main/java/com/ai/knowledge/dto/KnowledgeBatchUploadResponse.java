package com.ai.knowledge.dto;

import java.util.List;

/**
 * 批量知识上传响应。
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
