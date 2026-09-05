package com.ai.memory;

import com.ai.memory.dto.MemoryCompressionResult;

/**
 * 将原始对话文本压缩为可复用的记忆文本。
 *
 * @author data-agent
 */
public interface MemoryCompressor {

    /**
     * 压缩一轮已完成的对话。
     *
     * @param userMessage 用户消息
     * @param assistantReply 助手回复
     * @return 压缩结果
     */
    MemoryCompressionResult compressConversation(String userMessage, String assistantReply);

    /**
     * 压缩用户明确要求系统记住的内容。
     *
     * @param userMessage 用户消息
     * @param memoryType 分类后的记忆类型
     * @return 压缩结果
     */
    MemoryCompressionResult compressExplicitMemory(String userMessage, MemoryType memoryType);
}
