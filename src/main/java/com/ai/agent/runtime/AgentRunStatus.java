package com.ai.agent.runtime;

/**
 * 单次 Agent Run 的生命周期状态。
 *
 * @author data-agent
 */
public enum AgentRunStatus {

    CREATED,
    RUNNING,
    WAITING_APPROVAL,
    RESUMING,
    COMPLETED,
    FAILED,
    CANCELLED,
    TIMED_OUT,
    BUDGET_EXHAUSTED,
    REJECTED,
    EXPIRED;

    /**
     * 判断当前状态是否为终态。
     *
     * @return 是否为终态
     */
    public boolean isTerminal() {
        return this == COMPLETED
                || this == FAILED
                || this == CANCELLED
                || this == TIMED_OUT
                || this == BUDGET_EXHAUSTED
                || this == REJECTED
                || this == EXPIRED;
    }
}
