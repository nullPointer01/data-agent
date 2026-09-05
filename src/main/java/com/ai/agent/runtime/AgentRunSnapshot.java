package com.ai.agent.runtime;

import java.time.Instant;

/**
 * Agent Run 当前状态和资源消耗的只读快照。
 *
 * @param status 运行状态
 * @param terminationReason 终止原因
 * @param detail 安全的状态说明
 * @param startedAt 开始时间
 * @param deadline 截止时间
 * @param completedAt 结束时间
 * @param durationMs 已运行毫秒数
 * @param iterations 迭代数
 * @param modelCalls 模型调用数
 * @param toolCalls 工具调用数
 * @param tokens Token 消耗
 * @param tokenUsageEstimated 是否包含估算用量
 * @param tokenBudgetOvershoot Token 超出预算量
 * @param remainingActiveTimeoutMs 剩余 active execution 时间
 * @author data-agent
 */
public record AgentRunSnapshot(
        AgentRunStatus status,
        AgentRunTerminationReason terminationReason,
        String detail,
        Instant startedAt,
        Instant deadline,
        Instant completedAt,
        long durationMs,
        int iterations,
        int modelCalls,
        int toolCalls,
        long tokens,
        boolean tokenUsageEstimated,
        long tokenBudgetOvershoot,
        long remainingActiveTimeoutMs) {
}
