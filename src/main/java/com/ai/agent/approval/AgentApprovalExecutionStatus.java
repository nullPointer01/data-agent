package com.ai.agent.approval;

/**
 * 审批动作在恢复执行阶段的状态。
 *
 * @author data-agent
 */
public enum AgentApprovalExecutionStatus {
    WAITING_DECISION,
    READY,
    RUNNING,
    SUCCEEDED,
    FAILED,
    BLOCKED,
    CANCELLED
}
