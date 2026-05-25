package com.ai.knowledge.dto;

/**
 * Request for creating or updating text knowledge.
 *
 * @author data-agent
 */
public record KnowledgeTextRequest(String name, String content, String description) {
}
