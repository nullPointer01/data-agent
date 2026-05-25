package com.ai.memory.dto;

import com.ai.memory.MemorySource;
import com.ai.memory.MemoryTier;
import com.ai.memory.MemoryType;

import java.util.List;
import java.util.Map;

/**
 * Request used to capture one memory entry.
 *
 * @param tier memory storage tier
 * @param type memory type
 * @param source memory source
 * @param sessionId related session id
 * @param content original content
 * @param compressedContent summary content
 * @param metadata structured metadata
 * @param keyEntities extracted key entities
 * @param topicTags extracted topic tags
 * @param importance importance score in [1, 5]
 * @author data-agent
 */
public record MemoryCaptureRequest(
        MemoryTier tier,
        MemoryType type,
        MemorySource source,
        String sessionId,
        String content,
        String compressedContent,
        Map<String, Object> metadata,
        List<String> keyEntities,
        List<String> topicTags,
        int importance) {

    private static final int DEFAULT_IMPORTANCE = 3;

    /**
     * Creates a normalized request with safe defaults.
     *
     * @return normalized request
     */
    public MemoryCaptureRequest normalize() {
        return new MemoryCaptureRequest(
                tier == null ? MemoryTier.SHORT_TERM : tier,
                type == null ? MemoryType.CONVERSATION : type,
                source == null ? MemorySource.SYSTEM_GENERATED : source,
                sessionId,
                content,
                compressedContent,
                metadata == null ? Map.of() : metadata,
                keyEntities == null ? List.of() : keyEntities,
                topicTags == null ? List.of() : topicTags,
                importance <= 0 ? DEFAULT_IMPORTANCE : importance);
    }

    /**
     * Returns compressed content when available, otherwise original content.
     *
     * @return preferred memory text
     */
    public String effectiveContent() {
        if (compressedContent != null && !compressedContent.isBlank()) {
            return compressedContent;
        }
        return content;
    }
}
