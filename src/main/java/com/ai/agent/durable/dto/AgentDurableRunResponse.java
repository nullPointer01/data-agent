package com.ai.agent.durable.dto;

import java.time.Instant;

/**
 * Run 所有者可见的持久化状态，不包含 Checkpoint、密文或原始工具参数。
 *
 * @param runId Run ID
 * @param sessionId 会话 ID
 * @param agentId Agent ID
 * @param executionMode 执行模式
 * @param status 当前状态
 * @param terminationReason 稳定终止原因
 * @param statusDetail 安全状态摘要
 * @param approvalId 当前审批引用
 * @param approvalExpiresAt 审批过期时间
 * @param usedIterations 已用迭代数
 * @param usedModelCalls 已用模型调用数
 * @param usedToolCalls 已用工具调用数
 * @param usedTokens 已用 Token
 * @param remainingActiveTimeoutMs 剩余 active execution 时间
 * @param result 安全结果
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 * @param completedAt 终态时间
 * @author data-agent
 */
public record AgentDurableRunResponse(
        String runId,
        String sessionId,
        String agentId,
        String executionMode,
        String status,
        String terminationReason,
        String statusDetail,
        String approvalId,
        Instant approvalExpiresAt,
        int usedIterations,
        int usedModelCalls,
        int usedToolCalls,
        long usedTokens,
        long remainingActiveTimeoutMs,
        String result,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt) {
}
