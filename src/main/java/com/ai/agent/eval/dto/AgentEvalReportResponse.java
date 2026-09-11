package com.ai.agent.eval.dto;

import com.ai.agent.outcome.AgentOutcomeEvaluation;

import java.time.Instant;
import java.util.List;

/**
 * 一次版本化 Agent Eval 的聚合报告与样本级安全证据。
 *
 * @author data-agent
 */
public record AgentEvalReportResponse(
        boolean success,
        String evalRunId,
        String status,
        DatasetSummary dataset,
        AgentSnapshot agent,
        RunCounts counts,
        Metrics metrics,
        List<SampleResult> samples,
        String errorSummary,
        Instant startedAt,
        Instant completedAt) {

    public AgentEvalReportResponse {
        samples = samples == null ? List.of() : List.copyOf(samples);
    }

    /** 评测数据集版本摘要。 */
    public record DatasetSummary(String datasetId, String name, String description,
            int datasetVersion, String status, long enabledSamples) {
    }

    /** 评测绑定的 Agent 与运行配置快照。 */
    public record AgentSnapshot(String agentId, String agentProfileVersion, String modelId,
            String harnessConfigIdentity) {
    }

    /** 评测运行的样本计数。 */
    public record RunCounts(int total, int completed, int achieved, int notAchieved, int notEvaluated) {
    }

    /** 六类 Harness 指标与独立的 RAG 质量维度。 */
    public record Metrics(
            MetricValue taskCompletionRate,
            MetricValue correctToolSelectionRate,
            MetricValue invalidLoopRate,
            MetricValue approvalPolicyAccuracy,
            MetricValue p95DurationMs,
            MetricValue averageTokenUsage,
            MetricValue ragReferenceHitRate) {
    }

    /**
     * 可用或明确不可用的单项指标。
     *
     * @param value 百分比指标使用 0..100，其余按 unit 表示
     */
    public record MetricValue(boolean available, Double value, String unit,
            Integer numerator, Integer denominator, int excludedSamples, String unavailableReason) {
    }

    /** 单个固定样本的运行结果与可下钻 Run 引用。 */
    public record SampleResult(
            String sampleId,
            String sampleKey,
            String question,
            String outcomeStatus,
            String reasonCode,
            AgentOutcomeEvaluation outcomeEvaluation,
            List<String> expectedTools,
            List<String> observedTools,
            Boolean expectedApprovalRequired,
            Boolean approvalObserved,
            Boolean invalidLoopExpected,
            Boolean invalidLoopObserved,
            Long durationMs,
            Long tokenUsage,
            boolean tokenUsageEstimated,
            String agentRunId,
            String traceId,
            String errorSummary) {

        public SampleResult {
            expectedTools = expectedTools == null ? null : List.copyOf(expectedTools);
            observedTools = observedTools == null ? null : List.copyOf(observedTools);
        }
    }

    /** 评测运行列表项。 */
    public record RunSummary(String evalRunId, String status, String datasetId, int datasetVersion,
            String agentId, String modelId, RunCounts counts, Instant startedAt, Instant completedAt) {
    }
}
