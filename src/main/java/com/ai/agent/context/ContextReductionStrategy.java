package com.ai.agent.context;

import dev.langchain4j.data.message.ChatMessage;

import java.util.List;

/**
 * 上下文缩减策略。实现必须保留计划中标记的 protected 消息和工具协议完整性。
 *
 * @author data-agent
 */
public interface ContextReductionStrategy {

    /**
     * 返回稳定策略标识，供事件、Trace 和评测使用。
     *
     * @return 策略标识
     */
    String strategyId();

    /**
     * 在计划给定的输入预算内缩减消息。
     *
     * @param messages 完整消息序列
     * @param plan 上下文预算计划
     * @return 缩减结果
     */
    ReductionResult reduce(List<ChatMessage> messages, AgentContextPlan plan);

    /**
     * 缩减后的消息与 Token 估算。
     *
     * @param messages 保留消息
     * @param estimatedMessageTokens 保留消息 Token 估算
     */
    record ReductionResult(List<ChatMessage> messages, long estimatedMessageTokens) {

        public ReductionResult {
            messages = messages == null ? List.of() : List.copyOf(messages);
            if (estimatedMessageTokens < 0) {
                throw new IllegalArgumentException("缩减后 Token 估算不能为负数");
            }
        }
    }
}
