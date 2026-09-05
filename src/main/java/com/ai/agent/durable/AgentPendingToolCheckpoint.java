package com.ai.agent.durable;

import java.time.Instant;

/**
 * 暂停时尚未执行的工具动作。
 *
 * @param toolCallId 稳定工具调用编号
 * @param providerRequestId 模型协议中的原始请求编号
 * @param toolName 工具名称
 * @param argumentsJson 原始参数 JSON，仅随整体 Checkpoint 加密持久化
 * @param safeArgumentSummary 可用于审批展示的脱敏摘要
 * @param requestedAt 请求时间
 * @author data-agent
 */
public record AgentPendingToolCheckpoint(
        String toolCallId,
        String providerRequestId,
        String toolName,
        String argumentsJson,
        String safeArgumentSummary,
        Instant requestedAt) {

    public AgentPendingToolCheckpoint {
        requireText(toolCallId, "Checkpoint toolCallId 不能为空");
        requireText(toolName, "Checkpoint toolName 不能为空");
        requireText(providerRequestId, "Checkpoint providerRequestId 不能为空");
        requireText(argumentsJson, "Checkpoint argumentsJson 不能为空");
        safeArgumentSummary = safeArgumentSummary == null ? "" : safeArgumentSummary;
        if (requestedAt == null) {
            throw new IllegalArgumentException("Checkpoint requestedAt 不能为空");
        }
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
