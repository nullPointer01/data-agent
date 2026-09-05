package com.ai.knowledge.dto;

/**
 * 创建或更新文本知识请求。
 *
 * @author data-agent
 */
public record KnowledgeTextRequest(String name, String content, String description) {
}
