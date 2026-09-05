package com.ai.memory.dto;

import java.util.List;

/**
 * Agent Prompt 使用的聚合记忆上下文。
 *
 * @param workingMemory 当前会话工作记忆
 * @param shortTermMemories 近期摘要记忆
 * @param longTermContext 相关长期向量记忆
 * @param userProfile 当前用户画像快照
 * @author data-agent
 */
public record MemoryContext(
        String workingMemory,
        List<MemoryEntrySummary> shortTermMemories,
        String longTermContext,
        UserMemoryProfileSnapshotResponse userProfile) {

    /**
     * 返回空记忆上下文。
     *
     * @return 空上下文
     */
    public static MemoryContext empty() {
        return new MemoryContext("", List.of(), "", UserMemoryProfileSnapshotResponse.empty());
    }
}
