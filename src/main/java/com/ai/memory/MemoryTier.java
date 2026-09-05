package com.ai.memory;

/**
 * 记忆存储层级。
 *
 * @author data-agent
 */
public enum MemoryTier {

    /**
     * 会话级工作记忆，存储在 Redis 中。
     */
    WORKING,

    /**
     * 近期摘要记忆，存储在 MySQL 中。
     */
    SHORT_TERM,

    /**
     * 持久化记忆，索引到向量存储中。
     */
    LONG_TERM
}
