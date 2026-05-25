package com.ai.knowledge.dto;

/**
 * Per-file result for batch knowledge upload.
 *
 * @author data-agent
 */
public record KnowledgeBatchItemResponse(String filename, boolean success, String message) {
}
