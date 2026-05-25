package com.ai.memory;

/**
 * Memory storage tier.
 *
 * @author data-agent
 */
public enum MemoryTier {

    /**
     * Session-scoped working memory stored in Redis.
     */
    WORKING,

    /**
     * Recent summarized memory stored in MySQL.
     */
    SHORT_TERM,

    /**
     * Durable memory indexed into vector storage.
     */
    LONG_TERM
}
