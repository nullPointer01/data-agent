package com.ai.agent.approval.dto;

import java.time.Instant;

/**
 * 审批人可见的安全审批详情。
 *
 * @author data-agent
 */
public record AgentApprovalResponse(
        String approvalId,
        String runId,
        String toolCallId,
        String toolName,
        String risk,
        String requesterUserId,
        String safeArgumentSummary,
        String decisionStatus,
        String executionStatus,
        String reviewerUserId,
        String decisionComment,
        Instant requestedAt,
        Instant expiresAt,
        Instant decidedAt,
        Instant executionStartedAt,
        Instant executionCompletedAt) {
}
