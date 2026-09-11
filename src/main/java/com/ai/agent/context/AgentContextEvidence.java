package com.ai.agent.context;

/**
 * 可写入事件与 Trace 的安全上下文治理证据。
 *
 * <p>证据只包含数量、策略和稳定原因码，不包含消息正文、Prompt 或工具参数。</p>
 *
 * @param decision 准入决策
 * @param reason 稳定原因码
 * @param strategy 实际使用的策略
 * @param estimatedTokensBefore 治理前输入 Token 估算
 * @param estimatedTokensAfter 治理后输入 Token 估算
 * @param originalMessageCount 原消息数
 * @param retainedMessageCount 保留消息数
 * @param protectedMessageCount protected 消息数
 * @param modelWindowTokens 模型窗口
 * @param runRemainingTokens 当前 Run 剩余 Token
 * @param inputTokenLimit 本次输入上限
 * @param tokenEstimateUsed Token 是否为估算值
 * @author data-agent
 */
public record AgentContextEvidence(
        Decision decision,
        Reason reason,
        String strategy,
        long estimatedTokensBefore,
        long estimatedTokensAfter,
        int originalMessageCount,
        int retainedMessageCount,
        int protectedMessageCount,
        long modelWindowTokens,
        long runRemainingTokens,
        long inputTokenLimit,
        boolean tokenEstimateUsed) {

    public AgentContextEvidence {
        if (decision == null || reason == null) {
            throw new IllegalArgumentException("上下文决策与原因不能为空");
        }
        strategy = strategy == null || strategy.isBlank() ? "NONE" : strategy.trim();
        requireNonNegative(estimatedTokensBefore, "estimatedTokensBefore");
        requireNonNegative(estimatedTokensAfter, "estimatedTokensAfter");
        requireNonNegative(originalMessageCount, "originalMessageCount");
        requireNonNegative(retainedMessageCount, "retainedMessageCount");
        requireNonNegative(protectedMessageCount, "protectedMessageCount");
        if (modelWindowTokens <= 0) {
            throw new IllegalArgumentException("modelWindowTokens 必须为正数");
        }
        requireNonNegative(runRemainingTokens, "runRemainingTokens");
        requireNonNegative(inputTokenLimit, "inputTokenLimit");
        if (retainedMessageCount > originalMessageCount) {
            throw new IllegalArgumentException("保留消息数不能超过原消息数");
        }
    }

    /** 上下文准入结果。 */
    public enum Decision {
        ADMITTED,
        REDUCED,
        REJECTED
    }

    /** 稳定原因码，供 API、Trace 和评测使用。 */
    public enum Reason {
        WITHIN_BUDGET,
        MODEL_WINDOW_LIMIT,
        RUN_TOKEN_BUDGET_LIMIT,
        COMPACTION_DISABLED,
        CONSERVATIVE_TRIM_APPLIED,
        PROTECTED_CONTEXT_EXCEEDS_LIMIT,
        REDUCTION_INSUFFICIENT
    }

    private static void requireNonNegative(long value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " 不能为负数");
        }
    }
}
