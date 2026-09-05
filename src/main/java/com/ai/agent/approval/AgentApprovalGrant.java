package com.ai.agent.approval;

import java.time.Instant;
import java.util.Objects;

/**
 * 单个已批准动作的一次性内部执行凭据。
 *
 * @author data-agent
 */
public record AgentApprovalGrant(
        String approvalId,
        String runId,
        String tenantId,
        String requesterUserId,
        String reviewerUserId,
        String toolCallId,
        String toolName,
        Instant expiresAt) {

    public boolean validFor(String expectedRunId,
            String expectedTenantId,
            String expectedToolCallId,
            String expectedToolName) {
        return expiresAt != null
                && expiresAt.isAfter(Instant.now())
                && Objects.equals(runId, expectedRunId)
                && Objects.equals(tenantId, expectedTenantId)
                && Objects.equals(toolCallId, expectedToolCallId)
                && Objects.equals(toolName, expectedToolName);
    }
}
