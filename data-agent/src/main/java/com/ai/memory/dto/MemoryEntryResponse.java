package com.ai.memory.dto;

import com.ai.memory.MemoryEntry;
import com.ai.memory.MemorySource;
import com.ai.memory.MemoryTier;
import com.ai.memory.MemoryType;

import java.time.LocalDateTime;

/**
 * Memory entry response for memory governance APIs.
 *
 * @param memoryId memory id
 * @param sessionId related session id
 * @param tier memory tier
 * @param type memory type
 * @param source memory source
 * @param content preferred display content
 * @param importance normalized importance score
 * @param accessCount access count
 * @param createdAt creation time
 * @param expiresAt expiration time
 * @author data-agent
 */
public record MemoryEntryResponse(
        String memoryId,
        String sessionId,
        MemoryTier tier,
        MemoryType type,
        MemorySource source,
        String content,
        double importance,
        int accessCount,
        LocalDateTime createdAt,
        LocalDateTime expiresAt) {

    /**
     * Converts a persisted entry to API response.
     *
     * @param entry memory entry
     * @return response
     */
    public static MemoryEntryResponse from(MemoryEntry entry) {
        String displayContent = entry.getCompressedContent() != null && !entry.getCompressedContent().isBlank()
                ? entry.getCompressedContent()
                : entry.getContent();
        return new MemoryEntryResponse(
                entry.getMemoryId(),
                entry.getSessionId(),
                entry.getTier(),
                entry.getType(),
                entry.getSource(),
                displayContent,
                entry.getDecayWeight(),
                entry.getAccessCount(),
                entry.getCreatedAt(),
                entry.getExpiresAt());
    }
}
