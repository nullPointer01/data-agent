package com.ai.agent.react;

/**
 * 单次运行内错误恢复跟踪器生成的决策。
 *
 * @param recoveryRequired 当前观察是否需要恢复
 * @param allowed 是否允许继续恢复
 * @param attempt 当前恢复次数
 * @param maxAttempts 最大恢复次数
 * @author data-agent
 */
record ReActRecoveryDecision(boolean recoveryRequired, boolean allowed, int attempt, int maxAttempts) {

    static ReActRecoveryDecision none() {
        return new ReActRecoveryDecision(false, false, 0, 0);
    }
}
