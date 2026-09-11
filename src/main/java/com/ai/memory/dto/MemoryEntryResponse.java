package com.ai.memory.dto;

import com.ai.memory.MemoryEntry;
import com.ai.memory.MemorySource;
import com.ai.memory.MemoryTier;
import com.ai.memory.MemoryType;

import java.time.LocalDateTime;

/**
 * 记忆条目响应，用于记忆管理 API。
 *
 * @param memoryId 记忆编号
 * @param sessionId 关联的会话编号
 * @param tier 记忆层级
 * @param type 记忆类型
 * @param source 记忆来源
 * @param semanticKey 语义去重键
 * @param content 首选显示内容
 * @param confidence 提取置信度
 * @param importance 归一化的重要性分数
 * @param accessCount 访问次数
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 * @param expiresAt 过期时间
 * @author data-agent
 */
public record MemoryEntryResponse(
        String memoryId,
        String sessionId,
        MemoryTier tier,
        MemoryType type,
        MemorySource source,
        String semanticKey,
        String content,
        double confidence,
        double importance,
        int accessCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime expiresAt) {

    /**
     * 将持久化的记忆条目转换为 API 响应。
     *
     * @param entry 记忆条目
     * @return 响应
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
                entry.getSemanticKey(),
                displayContent,
                entry.getConfidence(),
                entry.getDecayWeight(),
                entry.getAccessCount(),
                entry.getCreatedAt(),
                entry.getUpdatedAt(),
                entry.getExpiresAt());
    }
}
