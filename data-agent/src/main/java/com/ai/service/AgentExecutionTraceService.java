package com.ai.service;

import com.ai.agent.IntentAnalysisResult;
import com.ai.agent.orchestrator.OrchestratorDecision;
import com.ai.agent.orchestrator.OrchestratorExecutionResult;
import com.ai.agent.orchestrator.OrchestrationPlan;
import com.ai.agent.orchestrator.OrchestratorResult;
import com.ai.agent.specialist.SpecialistResult;
import com.ai.agent.TaskClassification;
import com.ai.agent.dto.AgentExecutionTraceListResponse;
import com.ai.agent.dto.AgentExecutionTraceResponse;
import com.ai.logging.StructuredLogger;
import com.ai.model.AgentExecutionTrace;
import com.ai.model.AnalysisRequest;
import com.ai.model.ConversationSession;
import com.ai.repository.AgentExecutionTraceRepository;
import com.ai.security.SecurityContextHelper;
import com.fasterxml.jackson.core.JsonProcessingException;
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
import java.util.UUID;

/**
 * Agent 编排执行轨迹服务。
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
    private static final String UNKNOWN_VALUE = "UNKNOWN";
    private static final String CONTEXT_EXECUTION_METADATA = "execution_metadata";
    private static final String KEY_ORCHESTRATION_PLAN = "orchestrationPlan";
    private static final String KEY_REACT_EXECUTIONS = "reactExecutions";
    private static final String KEY_COLLABORATION_SUMMARY = "collaborationSummary";
    private static final String KEY_TASK_ID = "taskId";
    private static final String KEY_SPECIALIST_ID = "specialistId";
    private static final String KEY_SUCCESS = "success";
    private static final String KEY_SKIPPED = "skipped";
    private static final String KEY_STATUS = "status";
    private static final String KEY_SKIP_REASON = "skipReason";
    private static final String KEY_BLOCKING_DEPENDENCIES = "blockingDependencies";
    private static final String KEY_RESULT = "result";
    private static final String KEY_ERROR = "error";
    private static final String KEY_EXECUTION_TIME_MS = "executionTimeMs";
    private static final String KEY_OUTPUT_CONTEXT = "outputContext";
    private static final String KEY_NEXT_SUGGESTED_TASKS = "nextSuggestedTasks";
    private static final String KEY_EXECUTION_METADATA = "executionMetadata";
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
    private static final String OUTPUT_CONTEXT_STATUS = "status";
    private static final String OUTPUT_CONTEXT_SKIPPED = "skipped";
    private static final String OUTPUT_CONTEXT_SKIP_REASON = "skip_reason";
    private static final String OUTPUT_CONTEXT_BLOCKING_DEPENDENCIES = "blocking_dependencies";

    private final AgentExecutionTraceRepository traceRepository;
    private final SecurityContextHelper securityContextHelper;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final StructuredLogger structuredLogger;

    public AgentExecutionTraceService(AgentExecutionTraceRepository traceRepository,
            SecurityContextHelper securityContextHelper,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            StructuredLogger structuredLogger) {
        this.traceRepository = traceRepository;
        this.securityContextHelper = securityContextHelper;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.structuredLogger = structuredLogger;
    }

    /**
     * 记录一次 Orchestrator 编排执行轨迹。
     *
     * @param request 用户请求
     * @param session 当前会话
     * @param result 编排结果
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public String record(AnalysisRequest request, ConversationSession session, OrchestratorResult result) {
        if (result == null) {
            return null;
        }
        OrchestratorExecutionResult executionResult = result.executionResult();
        OrchestratorDecision decision = executionResult == null ? null : executionResult.decision();
        AgentExecutionTrace trace = new AgentExecutionTrace();
        String traceId = UUID.randomUUID().toString();
        trace.setTraceId(traceId);
        trace.setTenantId(securityContextHelper.getCurrentTenantId());
        trace.setUserId(securityContextHelper.getCurrentUserId());
        trace.setSessionId(resolveSessionId(request, session));
        trace.setQuestion(truncate(result.originalQuery()));
        trace.setSelectedAgent(result.selectedAgentName());
        trace.setSelectedType(result.selectedType().name());
        trace.setIntent(resolveIntent(decision));
        trace.setComplexity(resolveComplexity(decision));
        trace.setSuccess(result.success());
        trace.setFallbackUsed(executionResult != null && executionResult.fallbackUsed());
        trace.setTaskCount(executionResult == null ? 0 : executionResult.taskResults().size());
        trace.setDurationMs(result.durationMs());
        trace.setReason(truncate(decision == null ? "" : decision.reason()));
        trace.setError(truncate(result.error()));
        trace.setPlanJson(writeJson(buildPlanTracePayload(decision, executionResult), EMPTY_JSON_OBJECT));
        trace.setTaskResultsJson(writeJson(buildTaskResultTracePayload(executionResult), EMPTY_JSON_ARRAY));
        trace.setSharedContextJson(writeJson(buildSharedContextTracePayload(result, executionResult),
                EMPTY_JSON_OBJECT));
        traceRepository.save(trace);
        meterRegistry.counter(METRIC_AGENT_TRACE_TOTAL,
                TAG_SUCCESS, String.valueOf(result.success()),
                TAG_SELECTED_TYPE, trace.getSelectedType() == null ? UNKNOWN_VALUE : trace.getSelectedType()).increment();
        structuredLogger.logEvent(StructuredLogger.TYPE_AGENT_TRACE, buildTraceLogPayload(trace));
        return traceId;
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

    private String resolveSessionId(AnalysisRequest request, ConversationSession session) {
        if (session != null && StringUtils.hasText(session.getSessionId())) {
            return session.getSessionId();
        }
        return request != null ? request.getSessionId() : null;
    }

    private String resolveIntent(OrchestratorDecision decision) {
        IntentAnalysisResult intentAnalysis = decision == null ? null : decision.intentAnalysis();
        return intentAnalysis == null ? null : intentAnalysis.intent().name();
    }

    private String resolveComplexity(OrchestratorDecision decision) {
        TaskClassification classification = decision == null ? null : decision.classification();
        return classification == null ? null : classification.complexity().name();
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

    private Map<String, Object> buildPlanTracePayload(OrchestratorDecision decision,
            OrchestratorExecutionResult executionResult) {
        Map<String, Object> payload = new LinkedHashMap<>();
        OrchestrationPlan orchestrationPlan = decision == null ? null : decision.orchestrationPlan();
        if (orchestrationPlan != null) {
            // 保持顶层 tasks 字段，前端追踪页可以继续直接渲染编排任务。
            payload.put("planId", orchestrationPlan.planId());
            payload.put("originalQuery", orchestrationPlan.originalQuery());
            payload.put("tasks", orchestrationPlan.tasks());
            payload.put("executionPhases", orchestrationPlan.executionPhases());
            payload.put("sharedContextKeys", orchestrationPlan.sharedContextKeys());
            payload.put("createdAtMillis", orchestrationPlan.createdAtMillis());
        }
        payload.put(KEY_ORCHESTRATION_PLAN, orchestrationPlan);
        payload.put(KEY_REACT_EXECUTIONS, extractReActExecutionMetadata(executionResult));
        payload.put(KEY_COLLABORATION_SUMMARY, buildCollaborationSummary(executionResult));
        return payload;
    }

    private List<Map<String, Object>> buildTaskResultTracePayload(OrchestratorExecutionResult executionResult) {
        if (executionResult == null || executionResult.taskResults().isEmpty()) {
            return List.of();
        }
        return executionResult.taskResults().stream()
                .map(this::buildTaskResultPayload)
                .toList();
    }

    private Map<String, Object> buildTaskResultPayload(SpecialistResult result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(KEY_TASK_ID, result.taskId());
        payload.put(KEY_SPECIALIST_ID, result.specialistId());
        payload.put(KEY_SUCCESS, result.success());
        payload.put(KEY_STATUS, result.outputContext().getOrDefault(OUTPUT_CONTEXT_STATUS,
                result.success() ? "SUCCESS" : "FAILED"));
        payload.put(KEY_SKIPPED, Boolean.TRUE.equals(result.outputContext().get(OUTPUT_CONTEXT_SKIPPED)));
        payload.put(KEY_SKIP_REASON, result.outputContext().getOrDefault(OUTPUT_CONTEXT_SKIP_REASON, ""));
        payload.put(KEY_BLOCKING_DEPENDENCIES,
                result.outputContext().getOrDefault(OUTPUT_CONTEXT_BLOCKING_DEPENDENCIES, List.of()));
        payload.put(KEY_RESULT, result.result());
        payload.put(KEY_ERROR, result.error());
        payload.put(KEY_EXECUTION_TIME_MS, result.executionTimeMs());
        payload.put(KEY_OUTPUT_CONTEXT, result.outputContext());
        payload.put(KEY_NEXT_SUGGESTED_TASKS, result.nextSuggestedTasks());
        Object executionMetadata = result.outputContext().get(CONTEXT_EXECUTION_METADATA);
        if (executionMetadata != null) {
            payload.put(KEY_EXECUTION_METADATA, executionMetadata);
        }
        return payload;
    }

    private Map<String, Object> buildSharedContextTracePayload(OrchestratorResult result,
            OrchestratorExecutionResult executionResult) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (result != null && result.sharedContext() != null) {
            result.sharedContext().forEach((key, value) -> {
                if (!CONTEXT_EXECUTION_METADATA.equals(key)) {
                    payload.put(key, value);
                }
            });
        }
        payload.put(KEY_REACT_EXECUTIONS, extractReActExecutionMetadata(executionResult));
        payload.put(KEY_COLLABORATION_SUMMARY, buildCollaborationSummary(executionResult));
        return payload;
    }

    private Map<String, Object> buildCollaborationSummary(OrchestratorExecutionResult executionResult) {
        Map<String, Object> summary = new LinkedHashMap<>();
        if (executionResult == null || executionResult.taskResults().isEmpty()) {
            summary.put("totalTasks", 0);
            summary.put("successfulTasks", List.of());
            summary.put("failedTasks", List.of());
            summary.put("skippedTasks", List.of());
            summary.put("specialists", List.of());
            return summary;
        }
        List<String> successfulTasks = new java.util.ArrayList<>();
        List<String> failedTasks = new java.util.ArrayList<>();
        List<String> skippedTasks = new java.util.ArrayList<>();
        List<String> specialists = new java.util.ArrayList<>();
        for (SpecialistResult result : executionResult.taskResults()) {
            specialists.add(result.specialistId());
            if (isSkippedTask(result)) {
                skippedTasks.add(result.taskId());
                continue;
            }
            if (result.success()) {
                successfulTasks.add(result.taskId());
            } else {
                failedTasks.add(result.taskId());
            }
        }
        summary.put("totalTasks", executionResult.taskResults().size());
        summary.put("successfulTasks", List.copyOf(successfulTasks));
        summary.put("failedTasks", List.copyOf(failedTasks));
        summary.put("skippedTasks", List.copyOf(skippedTasks));
        summary.put("specialists", specialists.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .toList());
        return summary;
    }

    private boolean isSkippedTask(SpecialistResult result) {
        if (result == null) {
            return false;
        }
        return Boolean.TRUE.equals(result.outputContext().get(OUTPUT_CONTEXT_SKIPPED))
                || "SKIPPED".equals(result.outputContext().get(OUTPUT_CONTEXT_STATUS));
    }

    private List<Object> extractReActExecutionMetadata(OrchestratorExecutionResult executionResult) {
        if (executionResult == null || executionResult.taskResults().isEmpty()) {
            return List.of();
        }
        return executionResult.taskResults().stream()
                .map(SpecialistResult::outputContext)
                .map(outputContext -> outputContext.get(CONTEXT_EXECUTION_METADATA))
                .filter(metadata -> metadata != null)
                .toList();
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
