package com.ai.memory.dto;

import java.util.Map;

/**
 * 当前用户记忆体系统计响应。
 *
 * @param success 是否成功
 * @param totalMemories 记忆总数
 * @param workingMemoryCount 工作记忆数量
 * @param shortTermMemoryCount 短期记忆数量
 * @param longTermMemoryCount 长期记忆数量
 * @param typeCounts 按记忆类型统计的数量
 * @param sourceContentChars 写入记忆前的原始文本字符数
 * @param storedContentChars 实际进入记忆表的文本字符数
 * @param compressionRatio 压缩后文本占原始文本的比例
 * @param storageSavingRatio 节省的文本存储比例
 * @param maxMemoriesPerUser 单用户最大记忆配额
 * @param quotaUsage 配额占用比例
 * @param shortTermRetentionDays 短期记忆保留天数
 * @param longTermDecayAfterDays 长期记忆开始衰减天数
 * @param profileConfidence 用户画像置信度
 * @param contextBuildCount 记忆上下文构建次数
 * @param averageContextBuildMs 平均上下文构建耗时毫秒
 * @param maxContextBuildMs 最大上下文构建耗时毫秒
 * @author data-agent
 */
public record MemoryStatsResponse(
        boolean success,
        long totalMemories,
        long workingMemoryCount,
        long shortTermMemoryCount,
        long longTermMemoryCount,
        Map<String, Long> typeCounts,
        long sourceContentChars,
        long storedContentChars,
        double compressionRatio,
        double storageSavingRatio,
        int maxMemoriesPerUser,
        double quotaUsage,
        int shortTermRetentionDays,
        int longTermDecayAfterDays,
        double profileConfidence,
        long contextBuildCount,
        double averageContextBuildMs,
        double maxContextBuildMs) {

    /**
     * 当前用户身份不可用时返回空统计。
     *
     * @return 空统计响应
     */
    public static MemoryStatsResponse empty() {
        return new MemoryStatsResponse(false, 0L, 0L, 0L, 0L, Map.of(), 0L, 0L, 0D, 0D, 0, 0D, 0, 0,
                0D, 0L, 0D, 0D);
    }
}
