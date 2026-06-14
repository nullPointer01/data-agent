package com.ai.knowledge.dto;

import java.util.Map;

/**
 * 知识统计响应。
 *
 * @author data-agent
 */
public record KnowledgeStatsResponse(
        boolean success,
        int knowledgeCount,
        long totalChunks,
        long totalCharacters,
        int vectorStoreCount,
        Map<String, Long> vectorStoreByType,
        boolean usingMilvus) {
}
