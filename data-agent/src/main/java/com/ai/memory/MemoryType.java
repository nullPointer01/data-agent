package com.ai.memory;

/**
 * Semantic type of a memory entry.
 *
 * @author data-agent
 */
public enum MemoryType {

    /**
     * Raw or summarized conversation memory.
     */
    CONVERSATION,

    /**
     * Conversation summary memory.
     */
    SUMMARY,

    /**
     * User intent memory.
     */
    INTENT,

    /**
     * Entity memory such as person, company, product, or metric.
     */
    ENTITY,

    /**
     * Relationship memory between entities.
     */
    RELATION,

    /**
     * User preference memory.
     */
    PREFERENCE,

    /**
     * Behavioral pattern memory.
     */
    PATTERN,

    /**
     * Durable knowledge memory.
     */
    KNOWLEDGE,

    /**
     * Important conclusion memory.
     */
    CONCLUSION
}
