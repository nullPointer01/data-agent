package com.ai.memory.dto;

import com.ai.memory.MemoryEntry;
import com.ai.memory.MemorySource;
import com.ai.memory.MemoryTier;
import com.ai.memory.MemoryType;

import java.time.LocalDateTime;

/**
 * 用于 Prompt 上下文组装的轻量记忆条目。
 *
 * @param memoryId 记忆 ID
 * @param tier 记忆层级
 * @param type 记忆类型
 * @param source 记忆来源
 * @param content 摘要内容
 * @param decayWeight 排序权重
 * @param lastAccessedAt 最近访问时间
 * @author data-agent
 */
public record MemoryEntrySummary(
        String memoryId,
        MemoryTier tier,
        MemoryType type,
        MemorySource source,
        String content,
        double decayWeight,
        LocalDateTime lastAccessedAt) {

    /**
     * 将持久化记忆转换为轻量摘要。
     *
     * @param entry 持久化记忆
     * @return 摘要 DTO
     */
    public static MemoryEntrySummary from(MemoryEntry entry) {
        String text = entry.getCompressedContent() != null && !entry.getCompressedContent().isBlank()
                ? entry.getCompressedContent()
                : entry.getContent();
        return new MemoryEntrySummary(
                entry.getMemoryId(),
                entry.getTier(),
                entry.getType(),
                entry.getSource(),
                text,
                entry.getDecayWeight(),
                entry.getLastAccessedAt());
    }
}
