package com.ai.memory;

/**
 * 记忆维护任务执行结果。
 *
 * @param shortTermUpdatedCount 短期记忆衰减更新数量
 * @param shortTermDeletedCount 短期记忆衰减删除数量
 * @param longTermUpdatedCount 长期记忆衰减更新数量
 * @param promotedCount 晋升为长期记忆数量
 * @param expiredDeletedCount 过期清理数量
 * @author data-agent
 */
public record MemoryMaintenanceResult(
        int shortTermUpdatedCount,
        int shortTermDeletedCount,
        int longTermUpdatedCount,
        int promotedCount,
        int expiredDeletedCount) {
}
