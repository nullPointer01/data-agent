package com.ai.knowledge.dto;

/**
 * 批量知识上传的单文件结果。
 *
 * @author data-agent
 */
public record KnowledgeBatchItemResponse(String filename, boolean success, String message) {
}
