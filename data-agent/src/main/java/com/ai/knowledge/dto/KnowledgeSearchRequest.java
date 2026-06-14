package com.ai.knowledge.dto;

/**
 * 向量知识检索请求。
 *
 * @author data-agent
 */
public record KnowledgeSearchRequest(String query, Integer topK) {
}
