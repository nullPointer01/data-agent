package com.ai.agent.approval;

/**
 * 人工审批决定。决定一旦离开 PENDING 就不可修改。
 *
 * @author data-agent
 */
public enum AgentApprovalDecisionStatus {
    PENDING,
    APPROVED,
    REJECTED,
    EXPIRED,
    CANCELLED
}
