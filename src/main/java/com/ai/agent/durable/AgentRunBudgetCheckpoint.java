package com.ai.agent.durable;

import com.ai.agent.runtime.AgentRunLimits;

import java.time.Duration;

/**
 * Run 暂停时冻结的服务端限制和已消耗预算。
 *
 * @param timeoutMs 初始 active timeout
 * @param maxIterations 最大迭代数
 * @param maxModelCalls 最大模型调用数
 * @param maxToolCalls 最大工具调用数
 * @param maxTokens 最大 Token 数
 * @param usedIterations 已用迭代数
 * @param usedModelCalls 已用模型调用数
 * @param usedToolCalls 已用工具调用数
 * @param usedTokens 已用 Token 数
 * @param tokenUsageEstimated 是否包含估算 Token
 * @param remainingActiveTimeoutMs 剩余 active execution 时间
 * @author data-agent
 */
public record AgentRunBudgetCheckpoint(
        long timeoutMs,
        int maxIterations,
        int maxModelCalls,
        int maxToolCalls,
        long maxTokens,
        int usedIterations,
        int usedModelCalls,
        int usedToolCalls,
        long usedTokens,
        boolean tokenUsageEstimated,
        long remainingActiveTimeoutMs) {

    public AgentRunBudgetCheckpoint {
        if (timeoutMs <= 0 || remainingActiveTimeoutMs <= 0 || remainingActiveTimeoutMs > timeoutMs) {
            throw new IllegalArgumentException("Checkpoint active timeout 非法");
        }
        if (maxIterations <= 0 || maxModelCalls <= 0 || maxToolCalls <= 0 || maxTokens <= 0) {
            throw new IllegalArgumentException("Checkpoint Run 上限必须为正数");
        }
        if (usedIterations < 0 || usedIterations > maxIterations
                || usedModelCalls < 0 || usedModelCalls > maxModelCalls
                || usedToolCalls < 0 || usedToolCalls > maxToolCalls
                || usedTokens < 0 || usedTokens > maxTokens) {
            throw new IllegalArgumentException("Checkpoint 已用预算超出服务端上限");
        }
    }

    public AgentRunLimits toLimits() {
        return new AgentRunLimits(
                Duration.ofMillis(timeoutMs),
                maxIterations,
                maxModelCalls,
                maxToolCalls,
                maxTokens);
    }
}
