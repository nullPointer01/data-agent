package com.ai.agent.runtime.event;

/**
 * Agent Harness 内部使用的类型化事件。
 *
 * @author data-agent
 */
public enum AgentEventType {

    RUN_STARTED,
    RAG_CONTEXT,
    THINKING_START,
    MODEL_TOKEN,
    TOOL_CALL,
    APPROVAL_REQUIRED,
    REFLECTION,
    EXECUTION_PLAN,
    PARALLEL_PRECHECK,
    ORCHESTRATION,
    CONTEXT_GOVERNED,
    BUDGET_UPDATED,
    ERROR,
    RUN_PAUSED,
    RUN_TERMINATED;

    /**
     * 判断事件是否为 Run 终态事件。
     *
     * @return 是否为终态事件
     */
    public boolean isTerminal() {
        return this == RUN_PAUSED || this == RUN_TERMINATED;
    }
}
