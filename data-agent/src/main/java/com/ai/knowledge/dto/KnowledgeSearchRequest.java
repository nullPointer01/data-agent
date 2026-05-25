package com.ai.knowledge.dto;

/**
 * Request for vector knowledge retrieval.
 *
 * @author data-agent
 */
public record KnowledgeSearchRequest(String query, Integer topK) {
}
