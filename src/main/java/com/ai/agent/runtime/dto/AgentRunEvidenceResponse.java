package com.ai.agent.runtime.dto;

import com.ai.agent.outcome.AgentOutcomeEvaluation;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Run 所有者可见的安全运行证据。
 *
 * <p>保留原持久化 Run 响应字段，同时补充 Trace 关联、统一用量和安全事件投影。
 * 响应不包含 Checkpoint、原始工具参数、完整 Prompt 或隐藏推理。</p>
 *
 * @author data-agent
 */
public record AgentRunEvidenceResponse(
        String runId,
        String traceId,
        String sessionId,
        String agentId,
        String selectedAgent,
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
        boolean tokenUsageEstimated,
        long remainingActiveTimeoutMs,
        long durationMs,
        RunUsage usage,
        String result,
        Instant createdAt,
        Instant startedAt,
        Instant updatedAt,
        Instant completedAt,
        String evidenceSource,
        boolean eventHistoryComplete,
        boolean taskContractPresent,
        AgentOutcomeEvaluation outcomeEvaluation,
        List<RunEvent> events) {

    /**
     * 前端 Run Inspector 使用的统一资源用量。
     *
     * @author data-agent
     */
    public record RunUsage(
            long durationMs,
            int iterations,
            int modelCalls,
            int toolCalls,
            long tokens,
            boolean tokenUsageEstimated,
            long remainingActiveTimeoutMs) {
    }

    /**
     * 从持久化材料投影出的安全操作事件。
     *
     * @author data-agent
     */
    public record RunEvent(
            String type,
            Instant eventTime,
            String status,
            String title,
            String summary,
            String approvalId,
            Map<String, Object> details) {

        public RunEvent {
            details = details == null ? Map.of() : Map.copyOf(details);
        }
    }
}
