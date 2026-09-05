package com.ai.agent.tool.governance;

import java.time.Instant;

/**
 * 写入 Run Tool Journal 的安全执行摘要。
 *
 * @author data-agent
 */
public record AgentToolExecutionRecord(
        Instant occurredAt,
        String runId,
        String toolCallId,
        String toolName,
        AgentToolRiskLevel risk,
        AgentToolExecutionStatus status,
        boolean authorized,
        int attempts,
        long durationMs,
        String argumentSummary,
        boolean sanitized,
        boolean truncated,
        boolean retried) {

    public AgentToolExecutionRecord {
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
        argumentSummary = argumentSummary == null ? "{}" : argumentSummary;
    }
}
