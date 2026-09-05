package com.ai.memory.dto;

import com.ai.memory.MemorySource;
import com.ai.memory.MemoryTier;
import com.ai.memory.MemoryType;

import java.util.List;
import java.util.Map;

/**
 * 用于捕获一条记忆条目的请求。
 *
 * @param tier 记忆存储层级
 * @param type 记忆类型
 * @param source 记忆来源
 * @param sessionId 关联的会话编号
 * @param content 原始内容
 * @param compressedContent 摘要内容
 * @param metadata 结构化元数据
 * @param keyEntities 提取的关键实体
 * @param topicTags 提取的话题标签
 * @param importance 重要性分数，范围 [1, 5]
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
     * 创建具有安全默认值的规范化请求。
     *
     * @return 规范化的请求
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
     * 如果可用则返回压缩内容，否则返回原始内容。
     *
     * @return 首选的记忆文本
     */
    public String effectiveContent() {
        if (compressedContent != null && !compressedContent.isBlank()) {
            return compressedContent;
        }
        return content;
    }
}
