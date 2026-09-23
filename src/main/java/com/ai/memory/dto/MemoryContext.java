package com.ai.memory.dto;

/**
 * Agent Prompt 使用的聚合记忆上下文。
 *
 * @param conversationHistory 当前会话最近的原始消息，仅用于理解追问和指代
 * @param workingMemory 当前会话工作记忆
 * @param longTermContext 相关长期向量记忆
 * @param userProfile 当前用户画像快照
 * @author data-agent
 */
public record MemoryContext(
        String conversationHistory,
        String workingMemory,
        String longTermContext,
        UserMemoryProfileSnapshotResponse userProfile) {

    /**
     * 返回空记忆上下文。
     *
     * @return 空上下文
     */
    public static MemoryContext empty() {
        return new MemoryContext("", "", "", UserMemoryProfileSnapshotResponse.empty());
    }
}
