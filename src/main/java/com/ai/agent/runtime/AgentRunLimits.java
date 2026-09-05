package com.ai.agent.runtime;

import java.time.Duration;

/**
 * 由服务端分配给一次 Agent Run 的不可变限制。
 *
 * @param timeout 最大总耗时
 * @param maxIterations 最大推理迭代数
 * @param maxModelCalls 最大模型调用数
 * @param maxToolCalls 最大工具调用数
 * @param maxTokens 最大 Token 消耗
 * @author data-agent
 */
public record AgentRunLimits(
        Duration timeout,
        int maxIterations,
        int maxModelCalls,
        int maxToolCalls,
        long maxTokens) {

    public AgentRunLimits {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Agent Run timeout 必须为正数");
        }
        if (maxIterations <= 0) {
            throw new IllegalArgumentException("Agent Run maxIterations 必须为正数");
        }
        if (maxModelCalls <= 0) {
            throw new IllegalArgumentException("Agent Run maxModelCalls 必须为正数");
        }
        if (maxToolCalls <= 0) {
            throw new IllegalArgumentException("Agent Run maxToolCalls 必须为正数");
        }
        if (maxTokens <= 0) {
            throw new IllegalArgumentException("Agent Run maxTokens 必须为正数");
        }
    }
}
