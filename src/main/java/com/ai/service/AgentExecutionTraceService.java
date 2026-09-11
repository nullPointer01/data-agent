package com.ai.service;

import com.ai.agent.context.AgentContextGovernor;
import com.ai.agent.context.AgentContextGovernor.ContextEvidenceRecord;
import com.ai.agent.context.AgentContextEvidence;
import com.ai.agent.outcome.AgentCriterionEvaluation;
import com.ai.agent.outcome.AgentOutcomeEvaluation;
import com.ai.agent.outcome.AgentSuccessCriterion;
import com.ai.agent.outcome.AgentTaskContract;
import com.ai.agent.runtime.AgentRunContext;
import com.ai.agent.runtime.AgentRunRoute;
import com.ai.agent.runtime.AgentRunSnapshot;
import com.ai.agent.dto.AgentExecutionTraceListResponse;
import com.ai.agent.dto.AgentExecutionTraceResponse;
import com.ai.logging.StructuredLogger;
import com.ai.model.AgentExecutionTrace;
import com.ai.model.AnalysisResponse;
import com.ai.repository.AgentExecutionTraceRepository;
import com.ai.security.SecurityContextHelper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一 Agent Run 执行轨迹服务。
 *
 * @author data-agent
 */
@Service
public class AgentExecutionTraceService {

    private static final int DEFAULT_PAGE = 0;
    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 200;
    private static final int MAX_TEXT_LENGTH = 1024;
    private static final String EMPTY_JSON_OBJECT = "{}";
    private static final String EMPTY_JSON_ARRAY = "[]";
    private static final String METRIC_AGENT_TRACE_TOTAL = "data_agent_trace_total";
    private static final String TAG_SUCCESS = "success";
    private static final String TAG_SELECTED_TYPE = "selected_type";
    private static final String KEY_SUCCESS = "success";
    private static final String KEY_TRACE_ID = "traceId";
    private static final String KEY_TENANT_ID = "tenantId";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_SESSION_ID = "sessionId";
    private static final String KEY_SELECTED_AGENT = "selectedAgent";
    private static final String KEY_SELECTED_TYPE = "selectedType";
    private static final String KEY_INTENT = "intent";
    private static final String KEY_COMPLEXITY = "complexity";
    private static final String KEY_FALLBACK_USED = "fallbackUsed";
    private static final String KEY_TASK_COUNT = "taskCount";
    private static final String KEY_DURATION_MS = "durationMs";
    private static final String KEY_ERROR_LENGTH = "errorLength";
    private static final int MAX_CONTEXT_EVIDENCE_EVENTS = 64;

    private final AgentExecutionTraceRepository traceRepository;
    private final SecurityContextHelper securityContextHelper;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final StructuredLogger structuredLogger;
    private final AgentContextGovernor contextGovernor;

    public AgentExecutionTraceService(AgentExecutionTraceRepository traceRepository,
            SecurityContextHelper securityContextHelper,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            StructuredLogger structuredLogger,
            AgentContextGovernor contextGovernor) {
        this.traceRepository = traceRepository;
        this.securityContextHelper = securityContextHelper;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.structuredLogger = structuredLogger;
        this.contextGovernor = contextGovernor;
    }

    /**
     * 按统一 Agent Run 记录任意执行模式的轨迹，runId 与 traceId 使用同一标识。
     *
     * @param context Run 上下文
     * @param route 已解析路线
     * @param response 最终响应
     * @return 与 runId 相同的轨迹编号
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public String recordRun(AgentRunContext context, AgentRunRoute route, AnalysisResponse response) {
        AgentRunSnapshot snapshot = context.snapshot();

        AgentExecutionTrace trace = new AgentExecutionTrace();
        trace.setTraceId(context.runId());
        trace.setTenantId(context.tenantId());
        trace.setUserId(context.userId());
        trace.setSessionId(context.sessionId());
        trace.setQuestion(truncate(context.executionContext().getRequest().getQuestion()));
        trace.setSelectedAgent(resolveRunSelectedAgent(route));
        trace.setSelectedType(context.mode().name());
        trace.setIntent(route.requestPlan().intent().name());
        trace.setComplexity(null);
        trace.setSuccess(response != null && response.isSuccess());
        trace.setFallbackUsed(route.requestPlan().toolSelectionFallback());
        trace.setTaskCount(snapshot.toolCalls());
        trace.setDurationMs(snapshot.durationMs());
        trace.setReason(truncate(snapshot.terminationReason().name() + ": " + snapshot.detail()));
        trace.setError(truncate(response == null ? snapshot.detail() : response.getError()));
        trace.setPlanJson(writeJson(buildRunPlanPayload(snapshot, response, route),
                EMPTY_JSON_OBJECT));
        trace.setTaskResultsJson(EMPTY_JSON_ARRAY);
        trace.setSharedContextJson(writeJson(buildRunSharedContextPayload(context, snapshot, response, route),
                EMPTY_JSON_OBJECT));
        traceRepository.save(trace);
        meterRegistry.counter(METRIC_AGENT_TRACE_TOTAL,
                TAG_SUCCESS, String.valueOf(trace.isSuccess()),
                TAG_SELECTED_TYPE, trace.getSelectedType()).increment();
        structuredLogger.logEvent(StructuredLogger.TYPE_AGENT_TRACE, buildTraceLogPayload(trace));
        return context.runId();
    }

    /**
     * 将一次持久化恢复的安全结果追加到原 runId 对应 Trace。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void recordDurableResume(AgentRunContext context,
            String approvalId,
            String toolCallId,
            String outcome,
            String safeDetail,
            AgentOutcomeEvaluation outcomeEvaluation) {
        appendDurableResume(
                context.runId(),
                context.tenantId(),
                context.snapshot(),
                approvalId,
                toolCallId,
                outcome,
                safeDetail,
                context.taskContract(),
                outcomeEvaluation);
    }

    /**
     * 在 Checkpoint 尚未恢复为运行时 Context 时，仍按服务端持久化身份追加恢复结果。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void recordDurableResume(String runId,
            String tenantId,
            String approvalId,
            String toolCallId,
            String outcome,
            String safeDetail,
            AgentTaskContract taskContract,
            AgentOutcomeEvaluation outcomeEvaluation) {
        appendDurableResume(
                runId,
                tenantId,
                null,
                approvalId,
                toolCallId,
                outcome,
                safeDetail,
                taskContract,
                outcomeEvaluation);
    }

    /**
     * 追加非终态的恢复进度，不修改 Run 最终成功状态。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void recordDurableResumeProgress(String runId,
            String tenantId,
            String approvalId,
            String toolCallId,
            String outcome,
            String safeDetail) {
        AgentExecutionTrace trace = traceRepository
                .findByTraceIdAndTenantId(runId, tenantId)
                .orElse(null);
        if (trace == null) {
            return;
        }
        Map<String, Object> shared = readJsonObject(trace.getSharedContextJson());
        appendDurableResumeEvent(shared, approvalId, toolCallId, outcome, safeDetail, null);
        trace.setSharedContextJson(writeJson(shared, EMPTY_JSON_OBJECT));
        traceRepository.save(trace);
    }

    private void appendDurableResume(String runId,
            String tenantId,
            AgentRunSnapshot snapshot,
            String approvalId,
            String toolCallId,
            String outcome,
            String safeDetail,
            AgentTaskContract taskContract,
            AgentOutcomeEvaluation outcomeEvaluation) {
        AgentExecutionTrace trace = traceRepository
                .findByTraceIdAndTenantId(runId, tenantId)
                .orElse(null);
        if (trace == null) {
            return;
        }
        Map<String, Object> shared = readJsonObject(trace.getSharedContextJson());
        appendDurableResumeEvent(shared, approvalId, toolCallId, outcome, safeDetail, outcomeEvaluation);
        if (snapshot != null) {
            shared.put("run", buildRunMetadata(snapshot));
        }
        if (taskContract != null) {
            shared.put("taskContract", buildSafeTaskContract(taskContract));
        }
        if (outcomeEvaluation != null) {
            shared.put("outcomeEvaluation", buildSafeOutcomeEvaluation(outcomeEvaluation));
        }
        mergeContextGovernance(shared, runId);
        trace.setSharedContextJson(writeJson(shared, EMPTY_JSON_OBJECT));
        if (snapshot != null) {
            trace.setSuccess(snapshot.status() == com.ai.agent.runtime.AgentRunStatus.COMPLETED);
            trace.setDurationMs(snapshot.durationMs());
            trace.setTaskCount(snapshot.toolCalls());
            trace.setReason(truncate(snapshot.terminationReason().name() + ": " + snapshot.detail()));
            trace.setError(trace.isSuccess() ? null : truncate(safeDetail));
        } else {
            trace.setSuccess(false);
            trace.setReason(truncate((outcome == null ? "UNKNOWN" : outcome) + ": " + safeDetail));
            trace.setError(truncate(safeDetail));
        }
        traceRepository.save(trace);
    }

    private void appendDurableResumeEvent(Map<String, Object> shared,
            String approvalId,
            String toolCallId,
            String outcome,
            String safeDetail,
            AgentOutcomeEvaluation outcomeEvaluation) {
        List<Object> resumes = new java.util.ArrayList<>();
        Object existing = shared.get("durableResumes");
        if (existing instanceof List<?> list) {
            resumes.addAll(list);
        }
        Map<String, Object> resume = new LinkedHashMap<>();
        resume.put("approvalId", approvalId == null ? "" : approvalId);
        resume.put("toolCallId", toolCallId == null ? "" : toolCallId);
        resume.put("outcome", outcome == null ? "UNKNOWN" : outcome);
        resume.put("detail", truncate(safeDetail));
        resume.put("recordedAt", java.time.Instant.now());
        if (outcomeEvaluation != null) {
            resume.put("taskOutcomeStatus", outcomeEvaluation.status().name());
        }
        resumes.add(resume);
        shared.put("durableResumes", resumes);
    }

    private Map<String, Object> readJsonObject(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return new LinkedHashMap<>(objectMapper.readValue(
                    json, new TypeReference<Map<String, Object>>() { }));
        } catch (JsonProcessingException e) {
            return new LinkedHashMap<>();
        }
    }

    private String resolveRunSelectedAgent(AgentRunRoute route) {
        if (route.profile() != null) {
            return route.profile().getName();
        }
        return switch (route.target()) {
            case COMMAND -> "command";
            case SKILL -> route.mode().name().toLowerCase() + ":skill";
            case CONFIGURED_AGENT -> "configured-agent";
        };
    }

    private Map<String, Object> buildRunPlanPayload(AgentRunSnapshot snapshot,
            AnalysisResponse response,
            AgentRunRoute route) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.putAll(buildRunMetadata(snapshot));
        payload.put("requestPlan", route.requestPlan().toEvidence());
        if (response != null && response.getThinkingSteps() != null) {
            payload.put("thinkingSteps", response.getThinkingSteps());
        }
        return payload;
    }

    private Map<String, Object> buildRunSharedContextPayload(AgentRunContext context,
            AgentRunSnapshot snapshot,
            AnalysisResponse response,
            AgentRunRoute route) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("run", buildRunMetadata(snapshot));
        payload.put("requestPlan", route.requestPlan().toEvidence());
        payload.put("runOrigin", context.executionContext().getOrigin().name());
        Map<String, Object> toolGovernance = new LinkedHashMap<>();
        toolGovernance.put("records", context.toolJournal().snapshot());
        toolGovernance.put("overflowCount", context.toolJournal().overflowCount());
        toolGovernance.put("capacity", context.toolJournal().capacity());
        payload.put("toolGovernance", toolGovernance);
        payload.put("taskContract", buildSafeTaskContract(context.taskContract()));
        payload.put("outcomeEvaluation", response == null
                ? null : buildSafeOutcomeEvaluation(response.getOutcomeEvaluation()));
        payload.put("contextGovernance", buildContextGovernance(context.runId()));
        return payload;
    }

    private Map<String, Object> buildContextGovernance(String runId) {
        List<ContextEvidenceRecord> records = contextGovernor.evidenceForRun(runId);
        List<Map<String, Object>> events = records.stream()
                .map(this::buildSafeContextEvidence)
                .toList();
        Map<String, Object> contextGovernance = new LinkedHashMap<>();
        contextGovernance.put("events", events);
        contextGovernance.put("latest", events.isEmpty() ? null : events.get(events.size() - 1));
        contextGovernance.put("eventCount", events.size());
        return contextGovernance;
    }

    private void mergeContextGovernance(Map<String, Object> shared, String runId) {
        List<Object> merged = new java.util.ArrayList<>();
        Object existingGovernance = shared.get("contextGovernance");
        if (existingGovernance instanceof Map<?, ?> governance
                && governance.get("events") instanceof List<?> existingEvents) {
            merged.addAll(existingEvents);
        }
        for (ContextEvidenceRecord record : contextGovernor.evidenceForRun(runId)) {
            Map<String, Object> safeRecord = buildSafeContextEvidence(record);
            if (!merged.contains(safeRecord)) {
                merged.add(safeRecord);
            }
        }
        if (merged.size() > MAX_CONTEXT_EVIDENCE_EVENTS) {
            merged = new java.util.ArrayList<>(
                    merged.subList(merged.size() - MAX_CONTEXT_EVIDENCE_EVENTS, merged.size()));
        }
        Map<String, Object> governance = new LinkedHashMap<>();
        governance.put("events", merged);
        governance.put("latest", merged.isEmpty() ? null : merged.get(merged.size() - 1));
        governance.put("eventCount", merged.size());
        shared.put("contextGovernance", governance);
    }

    private Map<String, Object> buildSafeContextEvidence(ContextEvidenceRecord record) {
        AgentContextEvidence evidence = record.evidence();
        Map<String, Object> safe = new LinkedHashMap<>();
        safe.put("occurredAt", record.occurredAt().toString());
        safe.put("decision", evidence.decision().name());
        safe.put("reason", evidence.reason().name());
        safe.put("strategy", evidence.strategy());
        safe.put("estimatedTokensBefore", evidence.estimatedTokensBefore());
        safe.put("estimatedTokensAfter", evidence.estimatedTokensAfter());
        safe.put("originalMessageCount", evidence.originalMessageCount());
        safe.put("retainedMessageCount", evidence.retainedMessageCount());
        safe.put("protectedMessageCount", evidence.protectedMessageCount());
        safe.put("modelWindowTokens", evidence.modelWindowTokens());
        safe.put("runRemainingTokens", evidence.runRemainingTokens());
        safe.put("inputTokenLimit", evidence.inputTokenLimit());
        safe.put("tokenEstimateUsed", evidence.tokenEstimateUsed());
        return safe;
    }

    private AgentOutcomeEvaluation buildSafeOutcomeEvaluation(AgentOutcomeEvaluation evaluation) {
        if (evaluation == null) {
            return null;
        }
        List<AgentCriterionEvaluation> criteria = evaluation.criteria().stream()
                .map(this::buildSafeCriterionEvaluation)
                .toList();
        return new AgentOutcomeEvaluation(
                evaluation.status(),
                evaluation.evaluatorId(),
                evaluation.evaluatorVersion(),
                evaluation.reasonCode(),
                criteria);
    }

    private AgentCriterionEvaluation buildSafeCriterionEvaluation(AgentCriterionEvaluation evaluation) {
        List<String> evidenceReferences = evaluation.evidenceReferences().isEmpty()
                ? List.of()
                : List.of(safeEvidenceReference(evaluation.criterionType()));
        return new AgentCriterionEvaluation(
                evaluation.criterionId(),
                evaluation.criterionType(),
                evaluation.required(),
                evaluation.decision(),
                evaluation.evaluatorVersion(),
                evaluation.reasonCode(),
                evidenceReferences);
    }

    private String safeEvidenceReference(AgentSuccessCriterion.CriterionType criterionType) {
        if (criterionType == null) {
            return "run.evidence";
        }
        return switch (criterionType) {
            case ANSWER_CONTAINS -> "answer";
            case JSON_FIELD_EQUALS -> "answer.json";
            case TOOL_CALLED -> "toolJournal";
            case APPROVAL_STATUS -> "approval";
            case RUN_STATUS -> "run.status";
        };
    }

    private Map<String, Object> buildSafeTaskContract(AgentTaskContract taskContract) {
        if (taskContract == null) {
            return Map.of("present", false);
        }
        List<Map<String, Object>> criteria = taskContract.criteria().stream()
                .map(this::buildSafeCriterion)
                .toList();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("present", true);
        summary.put("goalLength", taskContract.goal().length());
        summary.put("criteria", criteria);
        return summary;
    }

    private Map<String, Object> buildSafeCriterion(AgentSuccessCriterion criterion) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("criterionId", criterion.criterionId());
        summary.put("type", criterion.type().name());
        summary.put("required", criterion.required());
        return summary;
    }

    private Map<String, Object> buildRunMetadata(AgentRunSnapshot snapshot) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", snapshot.status().name());
        payload.put("terminationReason", snapshot.terminationReason().name());
        payload.put("durationMs", snapshot.durationMs());
        payload.put("iterations", snapshot.iterations());
        payload.put("modelCalls", snapshot.modelCalls());
        payload.put("toolCalls", snapshot.toolCalls());
        payload.put("tokens", snapshot.tokens());
        payload.put("tokenUsageEstimated", snapshot.tokenUsageEstimated());
        payload.put("tokenBudgetOvershoot", snapshot.tokenBudgetOvershoot());
        payload.put("deadline", snapshot.deadline());
        return payload;
    }

    /**
     * 查询当前租户的执行轨迹列表。
     *
     * @param limit 返回条数
     * @param userId 可选用户编号
     * @return 执行轨迹列表
     */
    @Transactional(readOnly = true)
    public AgentExecutionTraceListResponse listCurrentTenant(int limit, String userId) {
        String tenantId = securityContextHelper.getCurrentTenantId();
        PageRequest page = PageRequest.of(DEFAULT_PAGE, Math.max(MIN_LIMIT, Math.min(limit, MAX_LIMIT)));
        List<AgentExecutionTrace> traces = StringUtils.hasText(userId)
                ? traceRepository.findByTenantIdAndUserIdOrderByCreatedAtDesc(tenantId, userId, page)
                : traceRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, page);
        return new AgentExecutionTraceListResponse(true, traces.stream()
                .map(AgentExecutionTraceResponse::from)
                .toList());
    }

    /**
     * 查询当前租户下单条执行轨迹。
     *
     * @param traceId 轨迹编号
     * @return 执行轨迹
     */
    @Transactional(readOnly = true)
    public AgentExecutionTraceResponse getCurrentTenantTrace(String traceId) {
        String tenantId = securityContextHelper.getCurrentTenantId();
        return traceRepository.findByTraceIdAndTenantId(traceId, tenantId)
                .map(AgentExecutionTraceResponse::from)
                .orElseThrow(() -> new IllegalArgumentException("执行轨迹不存在或无权限"));
    }

    private Map<String, Object> buildTraceLogPayload(AgentExecutionTrace trace) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(KEY_TRACE_ID, trace.getTraceId());
        payload.put(KEY_TENANT_ID, trace.getTenantId());
        payload.put(KEY_USER_ID, trace.getUserId());
        payload.put(KEY_SESSION_ID, trace.getSessionId());
        payload.put(KEY_SELECTED_AGENT, trace.getSelectedAgent());
        payload.put(KEY_SELECTED_TYPE, trace.getSelectedType());
        payload.put(KEY_INTENT, trace.getIntent());
        payload.put(KEY_COMPLEXITY, trace.getComplexity());
        payload.put(KEY_SUCCESS, trace.isSuccess());
        payload.put(KEY_FALLBACK_USED, trace.isFallbackUsed());
        payload.put(KEY_TASK_COUNT, trace.getTaskCount());
        payload.put(KEY_DURATION_MS, trace.getDurationMs());
        payload.put(KEY_ERROR_LENGTH, trace.getError() == null ? 0 : trace.getError().length());
        return payload;
    }

    private String writeJson(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return fallback;
        }
    }

    private String truncate(String value) {
        if (value == null || value.length() <= MAX_TEXT_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_TEXT_LENGTH);
    }
}
