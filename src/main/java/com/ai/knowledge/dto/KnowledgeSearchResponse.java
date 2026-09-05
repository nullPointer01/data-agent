package com.ai.knowledge.dto;

import java.util.List;

/**
 * 知识检索响应。
 *
 * @author data-agent
 */
public record KnowledgeSearchResponse(boolean success, List<KnowledgeSearchResult> results, String message) {

    public static KnowledgeSearchResponse success(List<KnowledgeSearchResult> results) {
        return new KnowledgeSearchResponse(true, results, null);
    }

    public static KnowledgeSearchResponse empty(String message) {
        return new KnowledgeSearchResponse(true, List.of(), message);
    }

    public static KnowledgeSearchResponse failure(String message) {
        return new KnowledgeSearchResponse(false, List.of(), message);
    }
}
