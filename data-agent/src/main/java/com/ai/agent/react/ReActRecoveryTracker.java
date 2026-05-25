package com.ai.agent.react;

import java.util.EnumMap;
import java.util.Map;

/**
 * 跟踪单次 ReAct 执行中的错误恢复次数。
 *
 * <p>该跟踪器由 ReActLoopRunner 按请求创建，恢复次数不会在用户、会话或并发请求之间泄漏。</p>
 *
 * @author data-agent
 */
class ReActRecoveryTracker {

    private final Map<ErrorRecoveryType, Integer> attempts = new EnumMap<>(ErrorRecoveryType.class);

    /**
     * 在恢复建议需要执行时记录一次尝试。
     *
     * @param advice 恢复建议
     * @return 恢复决策
     */
    ReActRecoveryDecision recordAttempt(ErrorRecoveryAdvice advice) {
        if (advice == null || !advice.recoveryRequired()) {
            return ReActRecoveryDecision.none();
        }
        int nextAttempt = attempts.getOrDefault(advice.type(), 0) + 1;
        if (nextAttempt > advice.maxAttempts()) {
            return new ReActRecoveryDecision(true, false, nextAttempt - 1, advice.maxAttempts());
        }
        attempts.put(advice.type(), nextAttempt);
        return new ReActRecoveryDecision(true, true, nextAttempt, advice.maxAttempts());
    }
}
