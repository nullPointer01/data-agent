package com.ai.service;

import com.ai.agent.AgentReasoningProperties;
import com.ai.agent.dto.AgentReasoningAcceptanceCheck;
import com.ai.agent.dto.AgentReasoningHealthResponse;
import com.ai.model.AgentExecutionTrace;
import com.ai.repository.AgentExecutionTraceRepository;
import com.ai.security.SecurityContextHelper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Agent 增强推理健康和验收状态服务。
 *
 * @author data-agent
 */
@Service
public class AgentReasoningHealthService {

    private static final int DEFAULT_PAGE = 0;
    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 500;
    private static final long SIMPLE_LATENCY_THRESHOLD_MS = 1_000L;
    private static final long COMPLEX_LATENCY_THRESHOLD_MS = 15_000L;
    private static final double RETRY_RECOVERY_RATE_THRESHOLD = 0.6D;
    private static final String STATUS_PASSED = "PASSED";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_FAILED = "FAILED";
    private static final String CHECK_FAST_PATH_CONFIG = "FAST_PATH_CONFIG";
    private static final String CHECK_FAST_PATH_SAMPLE = "FAST_PATH_SAMPLE";
    private static final String CHECK_PLANNING_CONFIG = "PLANNING_CONFIG";
    private static final String CHECK_COMPLEX_REASONING_SAMPLE = "COMPLEX_REASONING_SAMPLE";
    private static final String CHECK_PARALLEL_PRECHECK = "PARALLEL_PRECHECK";
    private static final String CHECK_ERROR_RECOVERY_CONFIG = "ERROR_RECOVERY_CONFIG";
    private static final String CHECK_RETRY_RECOVERY_RATE = "RETRY_RECOVERY_RATE";
    private static final String CHECK_REFLECTION_SAMPLE = "REFLECTION_SAMPLE";
    private static final String CHECK_WORKING_MEMORY = "WORKING_MEMORY";
    private static final String CHECK_SIMPLE_LATENCY = "SIMPLE_LATENCY";
    private static final String CHECK_COMPLEX_LATENCY = "COMPLEX_LATENCY";
    private static final String CHECK_TRACE_OBSERVABILITY = "TRACE_OBSERVABILITY";
    private static final String FIELD_MODE = "mode";
    private static final String FIELD_TYPE = "type";
    private static final String FIELD_PARALLEL = "parallel";
    private static final String FIELD_THINKING_STEPS = "thinkingSteps";
    private static final String FIELD_EXECUTION_PLAN = "executionPlan";
    private static final String FIELD_PARALLEL_PRECHECK = "parallelPrecheck";
    private static final String FIELD_MEMORY_CONTEXT = "memoryContext";
    private static final String MODE_FAST_PATH = "fast_path";
    private static final String MODE_REASONING = "reasoning";
    private static final String TYPE_REFLECTION = "reflection";
    private static final String TYPE_PARALLEL_PRECHECK = "parallel_precheck";
    private static final String COMPLEXITY_SIMPLE = "SIMPLE";
    private static final String COMPLEXITY_COMPLEX = "COMPLEX";

    private final AgentReasoningProperties reasoningProperties;
    private final AgentExecutionTraceRepository traceRepository;
    private final SecurityContextHelper securityContextHelper;
    private final ObjectMapper objectMapper;

    public AgentReasoningHealthService(AgentReasoningProperties reasoningProperties,
            AgentExecutionTraceRepository traceRepository,
            SecurityContextHelper securityContextHelper,
            ObjectMapper objectMapper) {
        this.reasoningProperties = reasoningProperties;
        this.traceRepository = traceRepository;
        this.securityContextHelper = securityContextHelper;
        this.objectMapper = objectMapper;
    }

    /**
     * 查询当前租户 Agent 增强推理验收状态。
     *
     * @param limit 最近执行轨迹样本数量
     * @return 增强推理健康状态
     */
    @Transactional(readOnly = true)
    public AgentReasoningHealthResponse getCurrentTenantHealth(int limit) {
        String tenantId = securityContextHelper.getCurrentTenantId();
        PageRequest page = PageRequest.of(DEFAULT_PAGE, normalizeLimit(limit));
        List<AgentExecutionTrace> traces = traceRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, page);
        ReasoningEvidenceSummary summary = summarize(traces);
        List<AgentReasoningAcceptanceCheck> checks = buildChecks(summary);
        List<String> issues = issues(checks);
        boolean healthy = checks.stream().noneMatch(check -> STATUS_FAILED.equals(check.status()));
        boolean accepted = checks.stream().allMatch(AgentReasoningAcceptanceCheck::passed);
        return new AgentReasoningHealthResponse(healthy,
                accepted,
                traces.size(),
                reasoningProperties.isFastPathEnabled(),
                reasoningProperties.isPlanningEnabled(),
                reasoningProperties.isParallelPrecheckEnabled(),
                reasoningProperties.isReflectionEnabled(),
                reasoningProperties.isWorkingMemoryEnabled(),
                summary.simpleSampleSize(),
                summary.complexSampleSize(),
                summary.averageSimpleDurationMs(),
                summary.averageComplexDurationMs(),
                summary.retryCandidateCount(),
                summary.retryRecoveredCount(),
                summary.retryRecoveryRate(),
                checks,
                issues);
    }

    private int normalizeLimit(int limit) {
        return Math.max(MIN_LIMIT, Math.min(limit, MAX_LIMIT));
    }

    private ReasoningEvidenceSummary summarize(List<AgentExecutionTrace> traces) {
        ReasoningEvidenceSummary summary = new ReasoningEvidenceSummary();
        for (AgentExecutionTrace trace : traces) {
            TraceReasoningEvidence evidence = extractEvidence(trace);
            summary.add(trace, evidence);
        }
        return summary;
    }

    private TraceReasoningEvidence extractEvidence(AgentExecutionTrace trace) {
        TraceReasoningEvidence evidence = new TraceReasoningEvidence();
        collectEvidence(trace.getPlanJson(), evidence);
        collectEvidence(trace.getTaskResultsJson(), evidence);
        collectEvidence(trace.getSharedContextJson(), evidence);
        String normalizedText = normalizeText(trace);
        evidence.fastPath = evidence.fastPath || normalizedText.contains(MODE_FAST_PATH)
                || normalizedText.contains("fast-answer");
        evidence.reasoning = evidence.reasoning || normalizedText.contains(MODE_REASONING);
        evidence.parallelPrecheck = evidence.parallelPrecheck
                || normalizedText.contains(TYPE_PARALLEL_PRECHECK)
                || normalizedText.contains(FIELD_PARALLEL_PRECHECK.toLowerCase(Locale.ROOT));
        evidence.reflection = evidence.reflection || normalizedText.contains(TYPE_REFLECTION)
                || normalizedText.contains("反思");
        evidence.workingMemory = evidence.workingMemory
                || normalizedText.contains(FIELD_MEMORY_CONTEXT.toLowerCase(Locale.ROOT))
                || normalizedText.contains("workingmemory");
        evidence.recovery = evidence.recovery || containsAny(normalizedText,
                "retry", "recovery", "recover", "重试", "恢复", "错误恢复");
        return evidence;
    }

    private void collectEvidence(String source, TraceReasoningEvidence evidence) {
        if (!StringUtils.hasText(source)) {
            return;
        }
        try {
            collectEvidence(objectMapper.readTree(source), evidence);
        } catch (Exception ignored) {
            // 历史轨迹可能不是合法 JSON，后续会通过关键词做兜底判断。
        }
    }

    private void collectEvidence(JsonNode node, TraceReasoningEvidence evidence) {
        if (node == null) {
            return;
        }
        if (node.isArray()) {
            node.forEach(item -> collectEvidence(item, evidence));
            return;
        }
        if (!node.isObject()) {
            return;
        }
        node.fields().forEachRemaining(entry -> {
            String fieldName = entry.getKey();
            JsonNode value = entry.getValue();
            collectFieldEvidence(fieldName, value, evidence);
            collectEvidence(value, evidence);
        });
    }

    private void collectFieldEvidence(String fieldName, JsonNode value, TraceReasoningEvidence evidence) {
        if (FIELD_MODE.equals(fieldName) && value.isTextual()) {
            String mode = value.asText();
            evidence.fastPath = evidence.fastPath || MODE_FAST_PATH.equalsIgnoreCase(mode);
            evidence.reasoning = evidence.reasoning || MODE_REASONING.equalsIgnoreCase(mode);
            return;
        }
        if (FIELD_TYPE.equals(fieldName) && value.isTextual()) {
            String type = value.asText();
            evidence.reflection = evidence.reflection || TYPE_REFLECTION.equalsIgnoreCase(type);
            evidence.parallelPrecheck = evidence.parallelPrecheck || TYPE_PARALLEL_PRECHECK.equalsIgnoreCase(type);
            return;
        }
        if (FIELD_PARALLEL.equals(fieldName) && value.isBoolean() && value.booleanValue()) {
            evidence.parallelPrecheck = true;
            return;
        }
        evidence.executionPlan = evidence.executionPlan || FIELD_EXECUTION_PLAN.equals(fieldName)
                || FIELD_THINKING_STEPS.equals(fieldName);
        evidence.parallelPrecheck = evidence.parallelPrecheck || FIELD_PARALLEL_PRECHECK.equals(fieldName);
        evidence.workingMemory = evidence.workingMemory || FIELD_MEMORY_CONTEXT.equals(fieldName);
    }

    private List<AgentReasoningAcceptanceCheck> buildChecks(ReasoningEvidenceSummary summary) {
        List<AgentReasoningAcceptanceCheck> checks = new ArrayList<>();
        checks.add(configCheck(CHECK_FAST_PATH_CONFIG, "快速路径配置", reasoningProperties.isFastPathEnabled(),
                "简单问题直答开关已启用", "快速路径开关未启用"));
        checks.add(sampleCheck(CHECK_FAST_PATH_SAMPLE, "简单问题快速路径", summary.simpleSampleSize() > 0L,
                "已发现 " + summary.simpleSampleSize() + " 条简单问题快速路径样本", "暂无 fast_path 轨迹样本"));
        checks.add(configCheck(CHECK_PLANNING_CONFIG, "任务规划配置", reasoningProperties.isPlanningEnabled(),
                "复杂问题规划开关已启用", "任务规划开关未启用"));
        checks.add(sampleCheck(CHECK_COMPLEX_REASONING_SAMPLE, "复杂问题增强推理", summary.complexSampleSize() > 0L,
                "已发现 " + summary.complexSampleSize() + " 条复杂推理样本", "暂无 reasoning/执行计划轨迹样本"));
        checks.add(configThenSampleCheck(CHECK_PARALLEL_PRECHECK, "并行预检", reasoningProperties.isParallelPrecheckEnabled(),
                summary.parallelSampleSize() > 0L, "已发现并行预检或并行阶段样本", "暂无并行预检或并行阶段样本"));
        checks.add(configCheck(CHECK_ERROR_RECOVERY_CONFIG, "错误恢复配置", reasoningProperties.isReflectionEnabled(),
                "反思和错误恢复策略已启用", "反思开关未启用，错误恢复能力不可验收"));
        checks.add(retryRecoveryCheck(summary));
        checks.add(configThenSampleCheck(CHECK_REFLECTION_SAMPLE, "反思样本", reasoningProperties.isReflectionEnabled(),
                summary.reflectionSampleSize() > 0L, "已发现反思步骤样本", "暂无 reflection 轨迹样本"));
        checks.add(configThenSampleCheck(CHECK_WORKING_MEMORY, "工作记忆", reasoningProperties.isWorkingMemoryEnabled(),
                summary.workingMemorySampleSize() > 0L, "已发现记忆上下文注入样本", "暂无 memoryContext 轨迹样本"));
        checks.add(latencyCheck(CHECK_SIMPLE_LATENCY, "简单问题响应耗时", summary.simpleSampleSize(),
                summary.averageSimpleDurationMs(), SIMPLE_LATENCY_THRESHOLD_MS));
        checks.add(latencyCheck(CHECK_COMPLEX_LATENCY, "复杂问题响应耗时", summary.complexSampleSize(),
                summary.averageComplexDurationMs(), COMPLEX_LATENCY_THRESHOLD_MS));
        checks.add(sampleCheck(CHECK_TRACE_OBSERVABILITY, "执行轨迹观测", summary.sampleSize() > 0L,
                "最近已采集 " + summary.sampleSize() + " 条执行轨迹", "当前租户暂无执行轨迹"));
        return checks;
    }

    private AgentReasoningAcceptanceCheck configCheck(String key, String name, boolean enabled,
            String passedDetail, String failedDetail) {
        return enabled ? check(key, name, STATUS_PASSED, passedDetail) : check(key, name, STATUS_FAILED, failedDetail);
    }

    private AgentReasoningAcceptanceCheck sampleCheck(String key, String name, boolean hasSample,
            String passedDetail, String pendingDetail) {
        return hasSample ? check(key, name, STATUS_PASSED, passedDetail)
                : check(key, name, STATUS_PENDING, pendingDetail);
    }

    private AgentReasoningAcceptanceCheck configThenSampleCheck(String key, String name, boolean enabled,
            boolean hasSample, String passedDetail, String pendingDetail) {
        if (!enabled) {
            return check(key, name, STATUS_FAILED, name + "配置未启用");
        }
        return sampleCheck(key, name, hasSample, passedDetail, pendingDetail);
    }

    private AgentReasoningAcceptanceCheck retryRecoveryCheck(ReasoningEvidenceSummary summary) {
        if (!reasoningProperties.isReflectionEnabled()) {
            return check(CHECK_RETRY_RECOVERY_RATE, "错误重试成功率", STATUS_FAILED, "反思开关未启用，无法统计恢复成功率");
        }
        if (summary.retryCandidateCount() == 0L) {
            return check(CHECK_RETRY_RECOVERY_RATE, "错误重试成功率", STATUS_PENDING,
                    "暂无错误恢复候选样本，需产生包含重试/恢复/反思的轨迹后验收");
        }
        boolean passed = summary.retryRecoveryRate() >= RETRY_RECOVERY_RATE_THRESHOLD;
        String detail = "当前 " + formatPercent(summary.retryRecoveryRate())
                + "，阈值 " + formatPercent(RETRY_RECOVERY_RATE_THRESHOLD)
                + "，样本 " + summary.retryRecoveredCount() + "/" + summary.retryCandidateCount();
        return check(CHECK_RETRY_RECOVERY_RATE, "错误重试成功率", passed ? STATUS_PASSED : STATUS_FAILED, detail);
    }

    private AgentReasoningAcceptanceCheck latencyCheck(String key, String name, long sampleSize,
            long actualMs, long thresholdMs) {
        if (sampleSize == 0L) {
            return check(key, name, STATUS_PENDING, "暂无对应轨迹样本，产生真实请求后可验收");
        }
        boolean passed = actualMs <= thresholdMs;
        return check(key, name, passed ? STATUS_PASSED : STATUS_FAILED,
                "当前 " + actualMs + " ms，阈值 " + thresholdMs + " ms，样本 " + sampleSize + " 条");
    }

    private AgentReasoningAcceptanceCheck check(String key, String name, String status, String detail) {
        return new AgentReasoningAcceptanceCheck(key, name, status, STATUS_PASSED.equals(status), detail);
    }

    private List<String> issues(List<AgentReasoningAcceptanceCheck> checks) {
        return checks.stream()
                .filter(check -> !check.passed())
                .map(check -> check.name() + "未通过：" + check.detail())
                .toList();
    }

    private String normalizeText(AgentExecutionTrace trace) {
        return String.join(" ",
                safeText(trace.getSelectedAgent()),
                safeText(trace.getSelectedType()),
                safeText(trace.getIntent()),
                safeText(trace.getComplexity()),
                safeText(trace.getReason()),
                safeText(trace.getError()),
                safeText(trace.getPlanJson()),
                safeText(trace.getTaskResultsJson()),
                safeText(trace.getSharedContextJson())).toLowerCase(Locale.ROOT);
    }

    private boolean containsAny(String source, String... keywords) {
        for (String keyword : keywords) {
            if (source.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private String formatPercent(double value) {
        return Math.round(value * 100D) + "%";
    }

    /**
     * 单条轨迹中可观察到的增强推理证据。
     */
    private static final class TraceReasoningEvidence {

        private boolean fastPath;
        private boolean reasoning;
        private boolean executionPlan;
        private boolean parallelPrecheck;
        private boolean reflection;
        private boolean workingMemory;
        private boolean recovery;
    }

    /**
     * 最近轨迹样本的推理验收聚合结果。
     */
    private static final class ReasoningEvidenceSummary {

        private long sampleSize;
        private long simpleSampleSize;
        private long complexSampleSize;
        private long parallelSampleSize;
        private long reflectionSampleSize;
        private long workingMemorySampleSize;
        private long retryCandidateCount;
        private long retryRecoveredCount;
        private long totalSimpleDurationMs;
        private long totalComplexDurationMs;

        private void add(AgentExecutionTrace trace, TraceReasoningEvidence evidence) {
            sampleSize++;
            if (isSimpleTrace(trace, evidence)) {
                simpleSampleSize++;
                totalSimpleDurationMs += trace.getDurationMs();
            }
            if (isComplexTrace(trace, evidence)) {
                complexSampleSize++;
                totalComplexDurationMs += trace.getDurationMs();
            }
            if (evidence.parallelPrecheck) {
                parallelSampleSize++;
            }
            if (evidence.reflection) {
                reflectionSampleSize++;
            }
            if (evidence.workingMemory) {
                workingMemorySampleSize++;
            }
            if (isRetryCandidate(trace, evidence)) {
                retryCandidateCount++;
                if (trace.isSuccess()) {
                    retryRecoveredCount++;
                }
            }
        }

        private boolean isSimpleTrace(AgentExecutionTrace trace, TraceReasoningEvidence evidence) {
            return evidence.fastPath || COMPLEXITY_SIMPLE.equalsIgnoreCase(safeComplexity(trace));
        }

        private boolean isComplexTrace(AgentExecutionTrace trace, TraceReasoningEvidence evidence) {
            return evidence.reasoning || evidence.executionPlan || COMPLEXITY_COMPLEX.equalsIgnoreCase(safeComplexity(trace));
        }

        private boolean isRetryCandidate(AgentExecutionTrace trace, TraceReasoningEvidence evidence) {
            return evidence.recovery || (evidence.reflection && StringUtils.hasText(trace.getError()));
        }

        private String safeComplexity(AgentExecutionTrace trace) {
            return trace.getComplexity() == null ? "" : trace.getComplexity();
        }

        private long sampleSize() {
            return sampleSize;
        }

        private long simpleSampleSize() {
            return simpleSampleSize;
        }

        private long complexSampleSize() {
            return complexSampleSize;
        }

        private long parallelSampleSize() {
            return parallelSampleSize;
        }

        private long reflectionSampleSize() {
            return reflectionSampleSize;
        }

        private long workingMemorySampleSize() {
            return workingMemorySampleSize;
        }

        private long retryCandidateCount() {
            return retryCandidateCount;
        }

        private long retryRecoveredCount() {
            return retryRecoveredCount;
        }

        private long averageSimpleDurationMs() {
            return simpleSampleSize == 0L ? 0L : Math.round((double) totalSimpleDurationMs / simpleSampleSize);
        }

        private long averageComplexDurationMs() {
            return complexSampleSize == 0L ? 0L : Math.round((double) totalComplexDurationMs / complexSampleSize);
        }

        private double retryRecoveryRate() {
            return retryCandidateCount == 0L ? 0D : (double) retryRecoveredCount / retryCandidateCount;
        }
    }
}
