package com.ai.knowledge.dto;

import java.util.List;

/**
 * Knowledge list response.
 *
 * @author data-agent
 */
public record KnowledgeListResponse(boolean success, List<KnowledgeItemResponse> knowledge, int total) {
}
