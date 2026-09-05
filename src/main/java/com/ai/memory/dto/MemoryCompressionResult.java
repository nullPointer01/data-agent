package com.ai.memory.dto;

import java.util.List;

/**
 * 记忆压缩策略产生的结果。
 *
 * @param content 规范化的源内容
 * @param compressedContent 压缩的记忆文本
 * @param keyEntities 提取的关键实体
 * @param topicTags 提取的话题标签
 * @author data-agent
 */
public record MemoryCompressionResult(
        String content,
        String compressedContent,
        List<String> keyEntities,
        List<String> topicTags) {

    /**
     * 应用不可变集合默认值。
     */
    public MemoryCompressionResult {
        keyEntities = keyEntities == null ? List.of() : List.copyOf(keyEntities);
        topicTags = topicTags == null ? List.of() : List.copyOf(topicTags);
    }
}
