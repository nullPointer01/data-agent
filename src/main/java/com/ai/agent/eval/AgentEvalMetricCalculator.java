package com.ai.agent.eval;

import com.ai.agent.eval.dto.AgentEvalReportResponse.MetricValue;
import com.ai.agent.eval.dto.AgentEvalReportResponse.Metrics;
import com.ai.agent.outcome.AgentOutcomeStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 从样本级稳定标签和运行证据计算 Agent Eval 指标。
 *
 * @author data-agent
 */
@Component
public class AgentEvalMetricCalculator {

    private static final String UNIT_PERCENT = "PERCENT";
    private static final String UNIT_MILLISECONDS = "MILLISECONDS";
    private static final String UNIT_TOKENS = "TOKENS";

    /**
     * 计算六类 Harness 指标，并保持 RAG 为独立维度。
     *
     * @param inputs 样本级标签和观察值
     * @return 聚合指标
     */
    public Metrics calculate(List<MetricInput> inputs) {
        List<MetricInput> safeInputs = inputs == null ? List.of() : List.copyOf(inputs);
        return new Metrics(
                taskCompletion(safeInputs),
                toolSelection(safeInputs),
                invalidLoop(safeInputs),
                approvalAccuracy(safeInputs),
                p95Duration(safeInputs),
                averageTokens(safeInputs),
                unavailable(safeInputs.size(), "RAG_SAMPLE_EVIDENCE_NOT_PERSISTED"));
    }

    /** 返回尚未执行完成时不可误读为零的指标集合。 */
    public Metrics unavailableMetrics(int totalSamples, String reason) {
        MetricValue value = unavailable(Math.max(0, totalSamples), reason);
        return new Metrics(value, value, value, value, value, value, value);
    }

    private MetricValue taskCompletion(List<MetricInput> inputs) {
        List<MetricInput> eligible = inputs.stream()
                .filter(item -> item.outcomeStatus() == AgentOutcomeStatus.ACHIEVED
                        || item.outcomeStatus() == AgentOutcomeStatus.NOT_ACHIEVED)
                .toList();
        int passed = (int) eligible.stream()
                .filter(item -> item.outcomeStatus() == AgentOutcomeStatus.ACHIEVED)
                .count();
        return percentage(passed, eligible.size(), inputs.size(), "TASK_OUTCOME_EVIDENCE_MISSING");
    }

    private MetricValue toolSelection(List<MetricInput> inputs) {
        List<MetricInput> eligible = inputs.stream()
                .filter(item -> item.expectedTools() != null && item.observedTools() != null)
                .toList();
        int matched = (int) eligible.stream()
                .filter(item -> Set.copyOf(item.expectedTools()).equals(Set.copyOf(item.observedTools())))
                .count();
        return percentage(matched, eligible.size(), inputs.size(), "TOOL_LABEL_OR_COMPLETE_JOURNAL_MISSING");
    }

    private MetricValue invalidLoop(List<MetricInput> inputs) {
        List<MetricInput> eligible = inputs.stream()
                .filter(item -> Boolean.FALSE.equals(item.invalidLoopExpected())
                        && item.invalidLoopObserved() != null)
                .toList();
        int unexpectedLoops = (int) eligible.stream()
                .filter(item -> Boolean.TRUE.equals(item.invalidLoopObserved()))
                .count();
        return percentage(unexpectedLoops, eligible.size(), inputs.size(), "LOOP_LABEL_OR_RUN_STATUS_MISSING");
    }

    private MetricValue approvalAccuracy(List<MetricInput> inputs) {
        List<MetricInput> eligible = inputs.stream()
                .filter(item -> item.expectedApprovalRequired() != null && item.approvalObserved() != null)
                .toList();
        int matched = (int) eligible.stream()
                .filter(item -> Objects.equals(item.expectedApprovalRequired(), item.approvalObserved()))
                .count();
        return percentage(matched, eligible.size(), inputs.size(), "APPROVAL_LABEL_OR_EVIDENCE_MISSING");
    }

    private MetricValue p95Duration(List<MetricInput> inputs) {
        List<Long> values = inputs.stream().map(MetricInput::durationMs).filter(Objects::nonNull)
                .sorted(Comparator.naturalOrder()).toList();
        if (values.isEmpty()) {
            return unavailable(inputs.size(), "RUN_DURATION_EVIDENCE_MISSING");
        }
        int index = Math.max(0, (int) Math.ceil(values.size() * 0.95D) - 1);
        return numeric(values.get(index).doubleValue(), UNIT_MILLISECONDS, values.size(), inputs.size());
    }

    private MetricValue averageTokens(List<MetricInput> inputs) {
        List<Long> values = inputs.stream().map(MetricInput::tokenUsage).filter(Objects::nonNull).toList();
        if (values.isEmpty()) {
            return unavailable(inputs.size(), "TOKEN_USAGE_EVIDENCE_MISSING");
        }
        double average = values.stream().mapToLong(Long::longValue).average().orElse(0D);
        return numeric(average, UNIT_TOKENS, values.size(), inputs.size());
    }

    private MetricValue percentage(int numerator, int denominator, int total, String reason) {
        if (denominator == 0) {
            return unavailable(total, reason);
        }
        double value = numerator * 100D / denominator;
        return new MetricValue(true, value, UNIT_PERCENT, numerator, denominator,
                Math.max(0, total - denominator), null);
    }

    private MetricValue numeric(double value, String unit, int denominator, int total) {
        return new MetricValue(true, value, unit, null, denominator,
                Math.max(0, total - denominator), null);
    }

    private MetricValue unavailable(int total, String reason) {
        return new MetricValue(false, null, null, null, 0, Math.max(0, total), reason);
    }

    /** 单个样本参与指标计算所需的最小信息。 */
    public record MetricInput(
            AgentOutcomeStatus outcomeStatus,
            List<String> expectedTools,
            List<String> observedTools,
            Boolean expectedApprovalRequired,
            Boolean approvalObserved,
            Boolean invalidLoopExpected,
            Boolean invalidLoopObserved,
            Long durationMs,
            Long tokenUsage) {

        public MetricInput {
            expectedTools = expectedTools == null ? null : List.copyOf(new ArrayList<>(expectedTools));
            observedTools = observedTools == null ? null : List.copyOf(new ArrayList<>(observedTools));
        }
    }
}
