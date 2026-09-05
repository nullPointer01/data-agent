package com.ai.memory;

/**
 * 记忆衰减维护结果。
 *
 * @param updatedCount 更新数量
 * @param deletedCount 删除数量
 * @author data-agent
 */
public record MemoryDecayResult(int updatedCount, int deletedCount) {
}
