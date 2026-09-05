package com.ai.agent.approval;

import java.time.Instant;

/**
 * 成功持久化审批暂停后的安全引用。
 *
 * @param approvalId 审批ID
 * @param runId Run ID
 * @param toolCallId 工具调用ID
 * @param toolName 工具名称
 * @param safeArgumentSummary 脱敏参数摘要
 * @param expiresAt 过期时间
 * @author data-agent
 */
public record AgentApprovalSuspension(
        String approvalId,
        String runId,
        String toolCallId,
        String toolName,
        String safeArgumentSummary,
        Instant expiresAt) {
}
