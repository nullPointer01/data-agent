package com.ai.knowledge.dto;

import java.util.List;

/**
 * 知识列表响应。
 *
 * @author data-agent
 */
public record KnowledgeListResponse(boolean success, List<KnowledgeItemResponse> knowledge, int total) {
}
