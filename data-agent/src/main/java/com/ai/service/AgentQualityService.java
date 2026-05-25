package com.ai.service;

import com.ai.agent.dto.AgentQualityAgentStatResponse;
import com.ai.agent.dto.AgentQualityAttributionResponse;
import com.ai.agent.dto.AgentQualityDashboardResponse;
import com.ai.agent.dto.AgentQualityRiskResponse;
import com.ai.agent.dto.AgentQualityTrendPointResponse;
import com.ai.model.AgentExecutionTrace;
import com.ai.model.AgentFeedback;
import com.ai.repository.AgentExecutionTraceRepository;
import com.ai.repository.AgentFeedbackRepository;
import com.ai.security.SecurityContextHelper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Agent 质量评估服务。
 *
 * @author data-agent
 */
@Service
public class AgentQualityService {

    private static final int DEFAULT_PAGE = 0;
    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 500;
    private static final int SCORE_MAX = 100;
    private static final int EXCELLENT_SCORE = 90;
    private static final int HEALTHY_SCORE = 75;
    private static final int WATCH_SCORE = 60;
    private static final long SLOW_DURATION_MS = 15_000L;
    private static final long CRITICAL_DURATION_MS = 45_000L;
    private static final int MAX_TREND_POINTS = 14;
    private static final String RATING_UP = "UP";
    private static final String RATING_DOWN = "DOWN";
    private static final String UNKNOWN_AGENT = "未命名 Agent";
    private static final String UNKNOWN_TYPE = "UNKNOWN";
    private static final String CAUSE_MODEL_CALL = "MODEL_CALL";
    private static final String CAUSE_TOOL_DATASOURCE = "TOOL_DATASOURCE";
    private static final String CAUSE_RAG_KNOWLEDGE = "RAG_KNOWLEDGE";
    private static final String CAUSE_SECURITY_QUOTA = "SECURITY_QUOTA";
    private static final String CAUSE_SLOW_EXECUTION = "SLOW_EXECUTION";
    private static final String CAUSE_FALLBACK_ROUTE = "FALLBACK_ROUTE";
    private static final String CAUSE_DEPENDENCY_BLOCKED = "DEPENDENCY_BLOCKED";
    private static final String CAUSE_REACT_ITERATION_LIMIT = "REACT_ITERATION_LIMIT";
    private static final String CAUSE_UNKNOWN = "UNKNOWN_FAILURE";
    private static final String KEY_TASK_ID = "taskId";
    private static final String KEY_SPECIALIST_ID = "specialistId";
    private static final String KEY_SUCCESS = "success";
    private static final String KEY_OUTPUT_CONTEXT = "outputContext";
    private static final String KEY_SKIPPED = "skipped";
    private static final String KEY_STATUS = "status";
    private static final String KEY_SKIP_REASON = "skipReason";
    private static final String KEY_BLOCKING_DEPENDENCIES = "blockingDependencies";
    private static final String STATUS_SKIPPED = "SKIPPED";
    private static final String KEY_REACT_EXECUTIONS = "reactExecutions";
    private static final String KEY_EXECUTION_METADATA = "executionMetadata";
    private static final String KEY_ITERATIONS = "iterations";
    private static final String KEY_THINKING_STEPS = "thinkingSteps";
    private static final String KEY_MODE = "mode";
    private static final String STEP_TYPE_MAX_ITERATIONS = "max_iterations";
    private static final int REACT_ITERATION_LIMIT = 8;

    private final AgentExecutionTraceRepository traceRepository;
    private final AgentFeedbackRepository feedbackRepository;
    private final SecurityContextHelper securityContextHelper;
    private final ObjectMapper objectMapper;

    public AgentQualityService(AgentExecutionTraceRepository traceRepository,
            AgentFeedbackRepository feedbackRepository,
            SecurityContextHelper securityContextHelper,
            ObjectMapper objectMapper) {
        this.traceRepository = traceRepository;
        this.feedbackRepository = feedbackRepository;
        this.securityContextHelper = securityContextHelper;
        this.objectMapper = objectMapper;
    }

    /**
     * 查询当前租户 Agent 质量看板。
     *
     * @param limit 最近执行轨迹样本数量
     * @return Agent 质量看板
     */
    @Transactional(readOnly = true)
    public AgentQualityDashboardResponse getCurrentTenantDashboard(int limit) {
        String tenantId = securityContextHelper.getCurrentTenantId();
        PageRequest page = PageRequest.of(DEFAULT_PAGE, normalizeLimit(limit));
        List<AgentExecutionTrace> traces = traceRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, page);
        List<AgentFeedback> recentFeedbacks = feedbackRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, page);
        QualityMetrics metrics = calculateMetrics(traces,
                feedbackRepository.countByTenantId(tenantId),
                feedbackRepository.countByTenantIdAndRating(tenantId, RATING_UP),
                feedbackRepository.countByTenantIdAndRating(tenantId, RATING_DOWN));
        int qualityScore = calculateQualityScore(metrics);
        List<AgentQualityRiskResponse> risks = buildRisks(traces, metrics);
        List<AgentQualityAttributionResponse> attributions = buildAttributions(traces);
        return new AgentQualityDashboardResponse(true,
                traces.size(),
                qualityScore,
                resolveQualityLevel(qualityScore),
                metrics.successRate(),
                metrics.positiveRate(),
                metrics.averageDurationMs(),
                metrics.fallbackRate(),
                metrics.totalFeedbackCount(),
                metrics.negativeFeedbackCount(),
                attributions,
                risks,
                buildRecommendations(risks, metrics, attributions),
                buildAgentStats(traces, recentFeedbacks),
                buildTrendPoints(traces, recentFeedbacks));
    }

    private int normalizeLimit(int limit) {
        return Math.max(MIN_LIMIT, Math.min(limit, MAX_LIMIT));
    }

    private QualityMetrics calculateMetrics(List<AgentExecutionTrace> traces,
            long totalFeedbackCount,
            long positiveFeedbackCount,
            long negativeFeedbackCount) {
        long traceCount = traces.size();
        long successCount = traces.stream().filter(AgentExecutionTrace::isSuccess).count();
        long fallbackCount = traces.stream().filter(AgentExecutionTrace::isFallbackUsed).count();
        long totalDurationMs = traces.stream().mapToLong(AgentExecutionTrace::getDurationMs).sum();
        double successRate = traceCount == 0L ? 0D : (double) successCount / traceCount;
        double fallbackRate = traceCount == 0L ? 0D : (double) fallbackCount / traceCount;
        long averageDurationMs = traceCount == 0L ? 0L : Math.round((double) totalDurationMs / traceCount);
        double positiveRate = totalFeedbackCount == 0L ? 0D : (double) positiveFeedbackCount / totalFeedbackCount;
        return new QualityMetrics(traceCount, successRate, fallbackRate, averageDurationMs,
                totalFeedbackCount, positiveFeedbackCount, negativeFeedbackCount, positiveRate);
    }

    private int calculateQualityScore(QualityMetrics metrics) {
        if (metrics.traceCount() == 0L) {
            return 0;
        }
        double feedbackScore = metrics.totalFeedbackCount() == 0L ? 0.8D : metrics.positiveRate();
        double score = metrics.successRate() * 45D
                + feedbackScore * 30D
                + calculateLatencyScore(metrics.averageDurationMs()) * 15D
                + (1D - metrics.fallbackRate()) * 10D;
        return (int) Math.round(Math.max(0D, Math.min(SCORE_MAX, score)));
    }

    private double calculateLatencyScore(long averageDurationMs) {
        if (averageDurationMs <= 0L || averageDurationMs <= 5_000L) {
            return 1D;
        }
        if (averageDurationMs >= CRITICAL_DURATION_MS) {
            return 0D;
        }
        return (double) (CRITICAL_DURATION_MS - averageDurationMs) / (CRITICAL_DURATION_MS - 5_000L);
    }

    private String resolveQualityLevel(int qualityScore) {
        if (qualityScore >= EXCELLENT_SCORE) {
            return "EXCELLENT";
        }
        if (qualityScore >= HEALTHY_SCORE) {
            return "HEALTHY";
        }
        if (qualityScore >= WATCH_SCORE) {
            return "WATCH";
        }
        return "RISK";
    }

    private List<AgentQualityRiskResponse> buildRisks(List<AgentExecutionTrace> traces, QualityMetrics metrics) {
        List<AgentQualityRiskResponse> risks = new ArrayList<>();
        if (metrics.traceCount() == 0L) {
            risks.add(new AgentQualityRiskResponse("NO_TRACE", "缺少执行样本", "LOW",
                    "当前租户暂无 Agent 执行轨迹，暂时无法判断运行质量。", "最近样本数为 0"));
            return risks;
        }
        if (metrics.successRate() < 0.85D) {
            risks.add(new AgentQualityRiskResponse("LOW_SUCCESS_RATE", "成功率偏低", "HIGH",
                    "最近执行成功率低于 85%，需要优先复盘失败轨迹和工具错误。",
                    formatPercent(metrics.successRate())));
        }
        if (metrics.averageDurationMs() > SLOW_DURATION_MS) {
            risks.add(new AgentQualityRiskResponse("HIGH_LATENCY", "平均耗时偏高", "MEDIUM",
                    "最近执行平均耗时超过 15 秒，可能影响交互体验。",
                    metrics.averageDurationMs() + " ms"));
        }
        if (metrics.fallbackRate() > 0.2D) {
            risks.add(new AgentQualityRiskResponse("FALLBACK_FREQUENT", "回退偏多", "MEDIUM",
                    "内置回退被频繁触发，说明编排、模型调用或专家执行存在不稳定点。",
                    formatPercent(metrics.fallbackRate())));
        }
        long dependencyBlockedCount = countDependencyBlockedTraces(traces);
        if (dependencyBlockedCount > 0L) {
            risks.add(new AgentQualityRiskResponse(CAUSE_DEPENDENCY_BLOCKED, "依赖阻断偏多",
                    resolveSeverity(dependencyBlockedCount, metrics.traceCount()),
                    "最近执行中存在上游任务失败后下游跳过的情况，说明编排依赖或任务拆解仍需优化。",
                    dependencyBlockedCount + " 条"));
        }
        if (hasIterationLimitTrace(traces)) {
            risks.add(new AgentQualityRiskResponse(CAUSE_REACT_ITERATION_LIMIT, "ReAct 达到迭代上限", "HIGH",
                    "最近轨迹中存在 ReAct 达到最大迭代次数的情况，说明问题拆解、工具选择或回退策略仍需优化。",
                    "max_iterations"));
        }
        if (metrics.totalFeedbackCount() == 0L) {
            risks.add(new AgentQualityRiskResponse("NO_FEEDBACK", "反馈样本不足", "LOW",
                    "当前还没有用户反馈，质量判断主要依赖执行指标。", "反馈数为 0"));
        } else if (metrics.positiveRate() < 0.7D) {
            risks.add(new AgentQualityRiskResponse("LOW_POSITIVE_RATE", "反馈满意度偏低", "HIGH",
                    "正向反馈率低于 70%，需要结合负向反馈定位准确性、检索和工具问题。",
                    formatPercent(metrics.positiveRate())));
        }
        return risks;
    }

    private List<String> buildRecommendations(List<AgentQualityRiskResponse> risks,
            QualityMetrics metrics,
            List<AgentQualityAttributionResponse> attributions) {
        List<String> recommendations = new ArrayList<>();
        for (AgentQualityRiskResponse risk : risks) {
            recommendations.add(suggestByRisk(risk.riskCode()));
        }
        for (AgentQualityAttributionResponse attribution : attributions) {
            recommendations.add(attribution.recommendation());
        }
        if (recommendations.isEmpty()) {
            recommendations.add("当前核心指标稳定，建议继续观察负向反馈和高耗时轨迹，保持提示词、工具和 RAG 配置的变更记录。");
        }
        if (metrics.negativeFeedbackCount() > 0L) {
            recommendations.add("优先复盘最近负向反馈关联的执行轨迹，确认问题来自检索缺口、工具失败还是模型回答偏差。");
        }
        return recommendations.stream().distinct().toList();
    }

    private String suggestByRisk(String riskCode) {
        return switch (riskCode) {
            case "NO_TRACE" -> "先通过智能对话产生真实执行轨迹，再用质量看板评估 Agent 运行稳定性。";
            case "LOW_SUCCESS_RATE" -> "按失败轨迹排序排查工具异常、数据源不可用和模型调用失败，并为高频失败场景补充测试。";
            case "HIGH_LATENCY" -> "检查慢轨迹中的任务数、RAG 召回数量和外部工具耗时，必要时增加快速路径或减少串行调用。";
            case "FALLBACK_FREQUENT" -> "复盘触发回退的编排决策，补齐专家 Agent 能力边界和错误恢复策略。";
            case "DEPENDENCY_BLOCKED" -> "检查编排依赖、上游任务失败和阶段阻断策略，必要时把关键前置任务拆小或加入失败中止。";
            case "NO_FEEDBACK" -> "在前端关键回答位置继续强化点赞/点踩入口，保证质量判断有用户侧信号。";
            case "LOW_POSITIVE_RATE" -> "按问题分类聚合负向反馈，优先修复准确性、RAG 缺口和工具执行失败三类问题。";
            default -> "持续关注执行轨迹、反馈和耗时指标，避免质量问题在生产环境累积。";
        };
    }

    private List<AgentQualityAttributionResponse> buildAttributions(List<AgentExecutionTrace> traces) {
        Map<String, AttributionAccumulator> attributionMap = new LinkedHashMap<>();
        long attentionCount = 0L;
        for (AgentExecutionTrace trace : traces) {
            if (!requiresAttention(trace)) {
                continue;
            }
            attentionCount++;
            String causeCode = resolveCauseCode(trace);
            attributionMap.computeIfAbsent(causeCode, ignored -> new AttributionAccumulator(causeCode))
                    .addTrace(trace);
        }
        long totalAttentionCount = attentionCount;
        return attributionMap.values().stream()
                .map((accumulator) -> accumulator.toResponse(totalAttentionCount))
                .sorted(Comparator.comparingLong(AgentQualityAttributionResponse::count).reversed())
                .toList();
    }

    private boolean requiresAttention(AgentExecutionTrace trace) {
        return !trace.isSuccess() || trace.isFallbackUsed() || trace.getDurationMs() > SLOW_DURATION_MS
                || hasIterationLimitTrace(trace);
    }

    private String resolveCauseCode(AgentExecutionTrace trace) {
        if (hasDependencyBlockedTrace(trace)) {
            return CAUSE_DEPENDENCY_BLOCKED;
        }
        if (hasIterationLimitTrace(trace)) {
            return CAUSE_REACT_ITERATION_LIMIT;
        }
        String evidence = normalizeEvidence(trace);
        if (containsAny(evidence, "quota", "token", "limit", "rate limit", "jwt", "auth", "permission",
                "forbidden", "unauthorized", "配额", "限流", "权限", "鉴权", "令牌")) {
            return CAUSE_SECURITY_QUOTA;
        }
        if (containsAny(evidence, "sql", "jdbc", "datasource", "database", "connection", "schema", "mysql",
                "postgres", "oracle", "工具执行失败", "数据源", "数据库", "连接失败")) {
            return CAUSE_TOOL_DATASOURCE;
        }
        if (containsAny(evidence, "rag", "vector", "milvus", "embedding", "knowledge", "retrieval",
                "向量", "检索", "知识库", "召回")) {
            return CAUSE_RAG_KNOWLEDGE;
        }
        if (containsAny(evidence, "model", "llm", "glm", "deepseek", "http", "circuit", "retry",
                "timeout", "timed out", "模型", "大模型", "熔断", "重试", "超时")) {
            return CAUSE_MODEL_CALL;
        }
        if (trace.getDurationMs() > SLOW_DURATION_MS) {
            return CAUSE_SLOW_EXECUTION;
        }
        if (trace.isFallbackUsed()) {
            return CAUSE_FALLBACK_ROUTE;
        }
        return CAUSE_UNKNOWN;
    }

    private String normalizeEvidence(AgentExecutionTrace trace) {
        return String.join(" ",
                safeText(trace.getPlanJson()),
                safeText(trace.getError()),
                safeText(trace.getReason()),
                safeText(trace.getTaskResultsJson()),
                safeText(trace.getSharedContextJson())).toLowerCase();
    }

    private boolean containsAny(String source, String... keywords) {
        for (String keyword : keywords) {
            if (source.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private boolean hasIterationLimitTrace(List<AgentExecutionTrace> traces) {
        return traces.stream().anyMatch(this::hasIterationLimitTrace);
    }

    private boolean hasIterationLimitTrace(AgentExecutionTrace trace) {
        if (trace == null) {
            return false;
        }
        for (JsonNode executionNode : readReactExecutionNodes(trace)) {
            if (containsIterationLimit(executionNode)) {
                return true;
            }
        }
        return containsAny(safeText(trace.getReason()), STEP_TYPE_MAX_ITERATIONS)
                || containsAny(safeText(trace.getPlanJson()), STEP_TYPE_MAX_ITERATIONS)
                || containsAny(safeText(trace.getTaskResultsJson()), STEP_TYPE_MAX_ITERATIONS)
                || containsAny(safeText(trace.getSharedContextJson()), STEP_TYPE_MAX_ITERATIONS);
    }

    private long countDependencyBlockedTraces(List<AgentExecutionTrace> traces) {
        return traces.stream()
                .filter(this::hasDependencyBlockedTrace)
                .count();
    }

    private boolean hasDependencyBlockedTrace(AgentExecutionTrace trace) {
        if (trace == null) {
            return false;
        }
        for (JsonNode taskResultNode : readTaskResultNodes(trace)) {
            if (containsDependencyBlocked(taskResultNode)) {
                return true;
            }
        }
        return containsAny(safeText(trace.getReason()), "skip", "跳过", "blocking_dependencies", "skippedTasks",
                "上游依赖失败")
                || containsAny(safeText(trace.getTaskResultsJson()), "SKIPPED", "skipReason", "blockingDependencies")
                || containsAny(safeText(trace.getSharedContextJson()), "skippedTasks", "blockingDependencies");
    }

    private List<JsonNode> readTaskResultNodes(AgentExecutionTrace trace) {
        List<JsonNode> taskResults = new ArrayList<>();
        collectTaskResultNodes(trace.getTaskResultsJson(), taskResults);
        return taskResults;
    }

    private void collectTaskResultNodes(String source, List<JsonNode> taskResults) {
        if (!StringUtils.hasText(source)) {
            return;
        }
        try {
            collectTaskResultNodes(objectMapper.readTree(source), taskResults);
        } catch (Exception ignored) {
            // 旧数据或损坏 JSON 直接回退到关键词匹配。
        }
    }

    private void collectTaskResultNodes(JsonNode node, List<JsonNode> taskResults) {
        if (node == null) {
            return;
        }
        if (node.isArray()) {
            node.forEach(item -> collectTaskResultNodes(item, taskResults));
            return;
        }
        if (node.isObject() && looksLikeTaskResultNode(node)) {
            taskResults.add(node);
        }
    }

    private boolean looksLikeTaskResultNode(JsonNode node) {
        return node.has(KEY_TASK_ID) && node.has(KEY_SPECIALIST_ID)
                && (node.has(KEY_SUCCESS) || node.has(KEY_OUTPUT_CONTEXT));
    }

    private boolean containsDependencyBlocked(JsonNode taskResultNode) {
        if (taskResultNode == null || !taskResultNode.isObject()) {
            return false;
        }
        if (isDependencyBlockedNode(taskResultNode)) {
            return true;
        }
        JsonNode outputContext = taskResultNode.get(KEY_OUTPUT_CONTEXT);
        return outputContext != null && outputContext.isObject() && isDependencyBlockedNode(outputContext);
    }

    private boolean isDependencyBlockedNode(JsonNode node) {
        if (node == null || !node.isObject()) {
            return false;
        }
        JsonNode skippedNode = node.get(KEY_SKIPPED);
        if (skippedNode != null && skippedNode.isBoolean() && skippedNode.booleanValue()) {
            return true;
        }
        JsonNode statusNode = node.get(KEY_STATUS);
        if (statusNode != null && STATUS_SKIPPED.equalsIgnoreCase(statusNode.asText())) {
            return true;
        }
        JsonNode skipReasonNode = node.get(KEY_SKIP_REASON);
        if (skipReasonNode != null && StringUtils.hasText(skipReasonNode.asText())) {
            return true;
        }
        JsonNode blockingDependenciesNode = node.get(KEY_BLOCKING_DEPENDENCIES);
        return blockingDependenciesNode != null
                && ((blockingDependenciesNode.isArray() && blockingDependenciesNode.size() > 0)
                || StringUtils.hasText(blockingDependenciesNode.asText()));
    }

    private String resolveSeverity(long count, long denominator) {
        double ratio = denominator == 0L ? 0D : (double) count / denominator;
        if (ratio >= 0.4D || count >= 5L) {
            return "HIGH";
        }
        if (ratio >= 0.2D || count >= 2L) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private List<JsonNode> readReactExecutionNodes(AgentExecutionTrace trace) {
        List<JsonNode> executions = new ArrayList<>();
        collectExecutionNodes(trace.getPlanJson(), executions);
        collectExecutionNodes(trace.getSharedContextJson(), executions);
        collectExecutionNodes(trace.getTaskResultsJson(), executions);
        return executions;
    }

    private void collectExecutionNodes(String source, List<JsonNode> executions) {
        if (!StringUtils.hasText(source)) {
            return;
        }
        try {
            collectExecutionNodes(objectMapper.readTree(source), executions);
        } catch (Exception ignored) {
            // 保持兼容旧文本字段；解析失败时回退到关键词归因。
        }
    }

    private void collectExecutionNodes(JsonNode node, List<JsonNode> executions) {
        if (node == null) {
            return;
        }
        if (node.isArray()) {
            node.forEach(item -> collectExecutionNodes(item, executions));
            return;
        }
        if (!node.isObject()) {
            return;
        }
        JsonNode reactExecutions = node.get(KEY_REACT_EXECUTIONS);
        if (reactExecutions != null && reactExecutions.isArray()) {
            reactExecutions.forEach(item -> collectExecutionNodes(item, executions));
        }
        JsonNode executionMetadata = node.get(KEY_EXECUTION_METADATA);
        if (executionMetadata != null && executionMetadata.isObject()) {
            executions.add(executionMetadata);
        }
        if (looksLikeExecutionNode(node)) {
            executions.add(node);
        }
    }

    private boolean looksLikeExecutionNode(JsonNode node) {
        return node.has(KEY_MODE) || node.has(KEY_ITERATIONS) || node.has(KEY_THINKING_STEPS);
    }

    private boolean containsIterationLimit(JsonNode executionNode) {
        if (executionNode == null || !executionNode.isObject()) {
            return false;
        }
        JsonNode iterationsNode = executionNode.get(KEY_ITERATIONS);
        if (iterationsNode != null && iterationsNode.canConvertToLong()
                && iterationsNode.asLong() >= REACT_ITERATION_LIMIT) {
            return true;
        }
        JsonNode thinkingSteps = executionNode.get(KEY_THINKING_STEPS);
        if (thinkingSteps != null && thinkingSteps.isArray()) {
            for (JsonNode step : thinkingSteps) {
                JsonNode typeNode = step.get("type");
                if (typeNode != null && STEP_TYPE_MAX_ITERATIONS.equals(typeNode.asText())) {
                    return true;
                }
            }
        }
        JsonNode modeNode = executionNode.get(KEY_MODE);
        return modeNode != null && "reasoning".equals(modeNode.asText())
                && iterationsNode != null
                && iterationsNode.canConvertToLong()
                && iterationsNode.asLong() >= REACT_ITERATION_LIMIT;
    }

    private List<AgentQualityAgentStatResponse> buildAgentStats(List<AgentExecutionTrace> traces,
            List<AgentFeedback> feedbacks) {
        Map<String, AgentStatAccumulator> stats = new LinkedHashMap<>();
        for (AgentExecutionTrace trace : traces) {
            String key = buildAgentKey(trace.getSelectedType(), trace.getSelectedAgent());
            stats.computeIfAbsent(key, ignored -> new AgentStatAccumulator(key, trace.getSelectedAgent(),
                    trace.getSelectedType())).addTrace(trace);
        }
        Map<String, AgentExecutionTrace> traceIndex = indexTraces(traces);
        for (AgentFeedback feedback : feedbacks) {
            AgentExecutionTrace trace = traceIndex.get(feedback.getTraceId());
            if (trace != null) {
                String key = buildAgentKey(trace.getSelectedType(), trace.getSelectedAgent());
                stats.computeIfAbsent(key, ignored -> new AgentStatAccumulator(key, trace.getSelectedAgent(),
                        trace.getSelectedType())).addFeedback(feedback);
            }
        }
        return stats.values().stream()
                .map(AgentStatAccumulator::toResponse)
                .sorted(Comparator.comparingLong(AgentQualityAgentStatResponse::traceCount).reversed())
                .toList();
    }

    /**
     * 按日期聚合趋势点，供前端渲染质量趋势图。
     *
     * @param traces 执行轨迹样本
     * @param feedbacks 反馈样本
     * @return 趋势点列表
     */
    private List<AgentQualityTrendPointResponse> buildTrendPoints(List<AgentExecutionTrace> traces,
            List<AgentFeedback> feedbacks) {
        Map<LocalDate, TrendAccumulator> trendMap = new TreeMap<>();
        for (AgentExecutionTrace trace : traces) {
            if (trace.getCreatedAt() == null) {
                continue;
            }
            trendMap.computeIfAbsent(trace.getCreatedAt().toLocalDate(), ignored -> new TrendAccumulator())
                    .addTrace(trace);
        }
        for (AgentFeedback feedback : feedbacks) {
            if (feedback.getCreatedAt() == null) {
                continue;
            }
            trendMap.computeIfAbsent(feedback.getCreatedAt().toLocalDate(), ignored -> new TrendAccumulator())
                    .addFeedback(feedback);
        }
        int startIndex = Math.max(0, trendMap.size() - MAX_TREND_POINTS);
        return trendMap.entrySet().stream()
                .skip(startIndex)
                .map(entry -> entry.getValue().toResponse(entry.getKey()))
                .toList();
    }

    private Map<String, AgentExecutionTrace> indexTraces(List<AgentExecutionTrace> traces) {
        Map<String, AgentExecutionTrace> index = new LinkedHashMap<>();
        for (AgentExecutionTrace trace : traces) {
            if (StringUtils.hasText(trace.getTraceId())) {
                index.put(trace.getTraceId(), trace);
            }
        }
        return index;
    }

    private String buildAgentKey(String selectedType, String selectedAgent) {
        String type = StringUtils.hasText(selectedType) ? selectedType : UNKNOWN_TYPE;
        String agent = StringUtils.hasText(selectedAgent) ? selectedAgent : UNKNOWN_AGENT;
        return type + ":" + agent;
    }

    private String formatPercent(double value) {
        return Math.round(value * 100D) + "%";
    }

    private record QualityMetrics(long traceCount,
            double successRate,
            double fallbackRate,
            long averageDurationMs,
            long totalFeedbackCount,
            long positiveFeedbackCount,
            long negativeFeedbackCount,
            double positiveRate) {
    }

    /**
     * 趋势聚合器，统一计算单日质量指标和展示分值。
     */
    private final class TrendAccumulator {

        private long traceCount;
        private long successCount;
        private long fallbackCount;
        private long totalDurationMs;
        private long feedbackCount;
        private long positiveFeedbackCount;
        private long negativeFeedbackCount;

        private void addTrace(AgentExecutionTrace trace) {
            traceCount++;
            if (trace.isSuccess()) {
                successCount++;
            }
            if (trace.isFallbackUsed()) {
                fallbackCount++;
            }
            totalDurationMs += trace.getDurationMs();
        }

        private void addFeedback(AgentFeedback feedback) {
            feedbackCount++;
            if (RATING_UP.equals(feedback.getRating())) {
                positiveFeedbackCount++;
            }
            if (RATING_DOWN.equals(feedback.getRating())) {
                negativeFeedbackCount++;
            }
        }

        private AgentQualityTrendPointResponse toResponse(LocalDate date) {
            double successRate = traceCount == 0L ? 0D : (double) successCount / traceCount;
            double fallbackRate = traceCount == 0L ? 0D : (double) fallbackCount / traceCount;
            long averageDurationMs = traceCount == 0L ? 0L : Math.round((double) totalDurationMs / traceCount);
            double positiveRate = feedbackCount == 0L ? 0D : (double) positiveFeedbackCount / feedbackCount;
            QualityMetrics metrics = new QualityMetrics(traceCount, successRate, fallbackRate, averageDurationMs,
                    feedbackCount, positiveFeedbackCount, negativeFeedbackCount, positiveRate);
            int qualityScore = calculateQualityScore(metrics);
            return new AgentQualityTrendPointResponse(date.toString(), traceCount, feedbackCount, successRate,
                    positiveRate, fallbackRate, averageDurationMs, qualityScore, positiveFeedbackCount,
                    negativeFeedbackCount);
        }
    }

    /**
     * 质量归因累加器，统一封装归因样本和展示文案。
     */
    private static final class AttributionAccumulator {

        private final String causeCode;
        private long count;
        private String sampleTraceId;
        private String sampleQuestion;

        private AttributionAccumulator(String causeCode) {
            this.causeCode = causeCode;
        }

        private void addTrace(AgentExecutionTrace trace) {
            count++;
            if (!StringUtils.hasText(sampleTraceId)) {
                sampleTraceId = trace.getTraceId();
                sampleQuestion = trace.getQuestion();
            }
        }

        private AgentQualityAttributionResponse toResponse(long totalAttentionCount) {
            double ratio = totalAttentionCount == 0L ? 0D : (double) count / totalAttentionCount;
            return new AgentQualityAttributionResponse(causeCode, causeName(), severity(ratio), count, ratio,
                    sampleTraceId, sampleQuestion, recommendation());
        }

        private String causeName() {
            return switch (causeCode) {
                case CAUSE_MODEL_CALL -> "模型调用异常";
                case CAUSE_TOOL_DATASOURCE -> "工具或数据源异常";
                case CAUSE_RAG_KNOWLEDGE -> "检索或知识库异常";
                case CAUSE_SECURITY_QUOTA -> "权限或配额限制";
                case CAUSE_SLOW_EXECUTION -> "执行耗时偏高";
                case CAUSE_FALLBACK_ROUTE -> "编排回退触发";
                case CAUSE_DEPENDENCY_BLOCKED -> "依赖阻断跳过";
                case CAUSE_REACT_ITERATION_LIMIT -> "ReAct 迭代上限";
                default -> "未知失败";
            };
        }

        private String severity(double ratio) {
            if (ratio >= 0.4D || count >= 5L) {
                return "HIGH";
            }
            if (ratio >= 0.2D || count >= 2L) {
                return "MEDIUM";
            }
            return "LOW";
        }

        private String recommendation() {
            return switch (causeCode) {
                case CAUSE_MODEL_CALL -> "检查模型配置、重试熔断、超时和供应商错误码，必要时启用备用模型。";
                case CAUSE_TOOL_DATASOURCE -> "优先复测数据源连接、SQL 安全策略和工具入参校验，给高频失败工具补充单测。";
                case CAUSE_RAG_KNOWLEDGE -> "检查向量索引、知识同步和召回阈值，确认回答依赖资料是否能被稳定召回。";
                case CAUSE_SECURITY_QUOTA -> "核对用户角色、Token 配额和限流策略，避免正常业务请求被误拦截。";
                case CAUSE_SLOW_EXECUTION -> "拆解慢轨迹中的串行步骤，减少不必要工具调用，并为简单问题配置快速路径。";
                case CAUSE_FALLBACK_ROUTE -> "复盘触发回退的意图识别和任务规划，明确专家 Agent 的能力边界。";
                case CAUSE_DEPENDENCY_BLOCKED -> "检查上游任务失败、编排依赖和阶段切分，必要时让关键任务失败即停止下游执行。";
                case CAUSE_REACT_ITERATION_LIMIT -> "检查问题拆解、工具选择和总结策略，降低 ReAct 反复迭代到上限的概率。";
                default -> "查看样本轨迹的错误、任务结果和共享上下文，补充更明确的错误分类规则。";
            };
        }
    }

    /**
     * Agent 分组统计累加器，避免在服务方法中散落可变统计字段。
     */
    private static final class AgentStatAccumulator {

        private final String agentKey;
        private final String agentName;
        private final String agentType;
        private long traceCount;
        private long successCount;
        private long fallbackCount;
        private long totalDurationMs;
        private long feedbackCount;
        private long positiveFeedbackCount;
        private long negativeFeedbackCount;

        private AgentStatAccumulator(String agentKey, String agentName, String agentType) {
            this.agentKey = agentKey;
            this.agentName = StringUtils.hasText(agentName) ? agentName : UNKNOWN_AGENT;
            this.agentType = StringUtils.hasText(agentType) ? agentType : UNKNOWN_TYPE;
        }

        private void addTrace(AgentExecutionTrace trace) {
            traceCount++;
            if (trace.isSuccess()) {
                successCount++;
            }
            if (trace.isFallbackUsed()) {
                fallbackCount++;
            }
            totalDurationMs += trace.getDurationMs();
        }

        private void addFeedback(AgentFeedback feedback) {
            feedbackCount++;
            if (RATING_UP.equals(feedback.getRating())) {
                positiveFeedbackCount++;
            }
            if (RATING_DOWN.equals(feedback.getRating())) {
                negativeFeedbackCount++;
            }
        }

        private AgentQualityAgentStatResponse toResponse() {
            double successRate = traceCount == 0L ? 0D : (double) successCount / traceCount;
            double fallbackRate = traceCount == 0L ? 0D : (double) fallbackCount / traceCount;
            long averageDurationMs = traceCount == 0L ? 0L : Math.round((double) totalDurationMs / traceCount);
            double positiveRate = feedbackCount == 0L ? 0D : (double) positiveFeedbackCount / feedbackCount;
            double feedbackScore = feedbackCount == 0L ? 0.8D : positiveRate;
            int qualityScore = (int) Math.round(successRate * 55D
                    + feedbackScore * 25D
                    + (1D - fallbackRate) * 20D);
            return new AgentQualityAgentStatResponse(agentKey, agentName, agentType, traceCount, successRate,
                    fallbackRate, averageDurationMs, feedbackCount, negativeFeedbackCount, positiveRate, qualityScore);
        }
    }
}
