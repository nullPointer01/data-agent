package com.ai.memory.dto;

import java.util.List;

/**
 * Result produced by a memory compression strategy.
 *
 * @param content normalized source content
 * @param compressedContent compressed memory text
 * @param keyEntities extracted key entities
 * @param topicTags extracted topic tags
 * @author data-agent
 */
public record MemoryCompressionResult(
        String content,
        String compressedContent,
        List<String> keyEntities,
        List<String> topicTags) {

    /**
     * Applies immutable collection defaults.
     */
    public MemoryCompressionResult {
        keyEntities = keyEntities == null ? List.of() : List.copyOf(keyEntities);
        topicTags = topicTags == null ? List.of() : List.copyOf(topicTags);
    }
}
