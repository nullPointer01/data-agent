package com.ai.memory;

import com.ai.memory.dto.MemoryCompressionResult;

/**
 * Compresses raw conversation text into reusable memory text.
 *
 * @author data-agent
 */
public interface MemoryCompressor {

    /**
     * Compresses one completed conversation turn.
     *
     * @param userMessage user message
     * @param assistantReply assistant reply
     * @return compression result
     */
    MemoryCompressionResult compressConversation(String userMessage, String assistantReply);

    /**
     * Compresses a user statement that explicitly asks the system to remember something.
     *
     * @param userMessage user message
     * @param memoryType classified memory type
     * @return compression result
     */
    MemoryCompressionResult compressExplicitMemory(String userMessage, MemoryType memoryType);
}
