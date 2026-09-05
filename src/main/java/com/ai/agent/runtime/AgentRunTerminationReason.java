package com.ai.agent.runtime;

/**
 * Agent Run 进入终态的稳定原因。
 *
 * @author data-agent
 */
public enum AgentRunTerminationReason {

    NONE,
    COMPLETED,
    FAILED,
    CANCELLED,
    INTERRUPTED,
    DEADLINE_EXCEEDED,
    ITERATION_LIMIT,
    MODEL_CALL_LIMIT,
    TOOL_CALL_LIMIT,
    TOKEN_LIMIT,
    APPROVAL_REJECTED,
    APPROVAL_EXPIRED,
    CHECKPOINT_INVALID,
    AUTHORIZATION_REVOKED,
    LEASE_LOST,
    RESUME_FAILED,
    TOOL_EXECUTION_FAILED
}
