package com.ai.agent.tool.governance;

/**
 * 工具执行的稳定终态和错误码。
 *
 * @author data-agent
 */
public enum AgentToolExecutionStatus {

    SUCCESS,
    UNKNOWN_TOOL,
    INVALID_ARGUMENTS,
    UNAUTHORIZED,
    POLICY_DENIED,
    APPROVAL_REQUIRED,
    TIMEOUT,
    EXECUTION_FAILED,
    RUN_TERMINATED
}
