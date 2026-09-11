package com.ai.agent.runtime;

import com.ai.agent.AgentExecutionContext;
import com.ai.agent.durable.AgentDurableRunStore;
import com.ai.agent.outcome.AgentOutcomeEvaluation;
import com.ai.agent.outcome.AgentOutcomeEvaluator;
import com.ai.agent.outcome.AgentOutcomeEvaluator.OutcomeEvidence;
import com.ai.agent.runtime.event.AgentEvent;
import com.ai.agent.runtime.event.AgentEventSink;
import com.ai.agent.runtime.event.AgentEventType;
import com.ai.agent.runtime.event.GuardedAgentEventSink;
import com.ai.agent.tool.AgentConversationRecorder;
import com.ai.agent.tool.governance.AgentToolAuthorizationService;
import com.ai.agent.tool.governance.AgentToolAuthorizationSnapshot;
import com.ai.agent.tool.governance.AgentToolExecutionJournal;
import com.ai.agent.tool.governance.AgentToolExecutionRecord;
import com.ai.agent.tool.governance.AgentToolExecutionStatus;
import com.ai.agent.tool.governance.AgentToolGovernanceProperties;
import com.ai.model.AnalysisRequest;
import com.ai.model.AnalysisResponse;
import com.ai.model.ConversationSession;
import com.ai.security.SecurityContextHelper;
import com.ai.service.AgentExecutionTraceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

/**
 * 统一管理一次 Agent Run 的路由、生命周期、事件、会话和追踪。
 *
 * @author data-agent
 */
@Component
public class AgentRunCoordinator {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentRunCoordinator.class);
    private static final Pattern SECRET_PATTERN = Pattern.compile(
            "(?i)(bearer\\s+|api[_-]?key\\s*[=:]\\s*|sk-)[a-z0-9._-]{8,}");

    private final AgentRunRouteResolver routeResolver;
    private final AgentRuntimeProperties runtimeProperties;
    private final AgentRunRegistry runRegistry;
    private final SecurityContextHelper securityContextHelper;
    private final AgentConversationRecorder conversationRecorder;
    private final AgentExecutionTraceService traceService;
    private final AgentToolAuthorizationService toolAuthorizationService;
    private final AgentToolGovernanceProperties toolGovernanceProperties;
    private final AgentDurableRunStore durableRunStore;
    private final AgentOutcomeEvaluator outcomeEvaluator;
    private final Map<AgentExecutionMode, AgentExecutionStrategy> strategies;

    public AgentRunCoordinator(AgentRunRouteResolver routeResolver,
            AgentRuntimeProperties runtimeProperties,
            AgentRunRegistry runRegistry,
            SecurityContextHelper securityContextHelper,
            AgentConversationRecorder conversationRecorder,
            AgentToolAuthorizationService toolAuthorizationService,
            AgentToolGovernanceProperties toolGovernanceProperties,
            AgentDurableRunStore durableRunStore,
            AgentOutcomeEvaluator outcomeEvaluator,
            @Nullable AgentExecutionTraceService traceService,
            List<AgentExecutionStrategy> executionStrategies) {
        this.routeResolver = routeResolver;
        this.runtimeProperties = runtimeProperties;
        this.runRegistry = runRegistry;
        this.securityContextHelper = securityContextHelper;
        this.conversationRecorder = conversationRecorder;
        this.toolAuthorizationService = toolAuthorizationService;
        this.toolGovernanceProperties = toolGovernanceProperties;
        this.durableRunStore = durableRunStore;
        this.outcomeEvaluator = outcomeEvaluator;
        this.traceService = traceService;
        this.strategies = indexStrategies(executionStrategies);
    }

    /**
     * 使用同步事件接收端执行一次 Run。
     *
     * @param executionContext 已准备的业务上下文
     * @return 带 Run 元数据的分析响应
     */
    public AnalysisResponse execute(AgentExecutionContext executionContext) {
        return execute(executionContext, AgentEventSink.noop());
    }

    /**
     * 使用指定事件接收端执行一次 Run。
     *
     * @param executionContext 已准备的业务上下文
     * @param eventSink 传输无关的事件接收端
     * @return 带 Run 元数据的完整响应
     */
    public AnalysisResponse execute(AgentExecutionContext executionContext, AgentEventSink eventSink) {
        if (executionContext == null || executionContext.getRequest() == null) {
            throw new IllegalArgumentException("Agent 执行上下文不能为空");
        }
        AgentRunRoute route = routeResolver.resolve(executionContext);
        AgentRunContext runContext = createRunContext(executionContext, route, eventSink);
        AgentExecutionStrategy.Result strategyResult = null;
        AnalysisResponse response = null;
        if (durableRunStore.isEnabled()) {
            durableRunStore.create(runContext);
        }
        runRegistry.register(runContext);
        LOGGER.info("Agent Run 已创建: runId={}, mode={}, target={}",
                runContext.runId(), runContext.mode(), route.target());
        try {
            if (!runContext.control().start()) {
                throw new IllegalStateException("Agent Run 无法启动");
            }
            if (durableRunStore.isEnabled() && !durableRunStore.transition(
                    runContext.runId(),
                    new com.ai.agent.durable.AgentRunTransition(
                            AgentRunStatus.CREATED,
                            AgentRunStatus.RUNNING,
                            AgentRunTerminationReason.NONE,
                            "运行已启动"))) {
                throw new IllegalStateException("持久化 Agent Run 无法启动");
            }
            strategyResult = AgentRunScope.call(runContext, () -> {
                emitStarted(runContext);
                emitRequestPlan(runContext, route);
                runContext.control().ensureActive();
                return executeStrategy(runContext, route);
            });
            response = strategyResult == null ? null : strategyResult.response();
            if (runContext.snapshot().status() == AgentRunStatus.WAITING_APPROVAL) {
                response = response == null ? AnalysisResponse.ok(null) : response;
            } else if (response == null) {
                runContext.control().fail("Agent 执行结果为空");
                response = AnalysisResponse.fail("Agent 执行结果为空");
            } else if (response.isSuccess()) {
                runContext.control().ensureActive();
                runContext.control().complete();
            } else {
                runContext.control().fail(sanitize(response.getError()));
            }
        } catch (AgentRunTerminatedException e) {
            response = AnalysisResponse.fail(safeDetail(e.getSnapshot(), "Agent 运行已终止"));
        } catch (Exception e) {
            String safeError = safeMessage(e);
            LOGGER.error("Agent Run 执行失败: runId={}, errorType={}, message={}",
                    runContext.runId(), e.getClass().getSimpleName(), safeError);
            runContext.control().fail(safeError);
            response = AnalysisResponse.fail(safeError);
        } finally {
            try {
                synchronizeDurableTerminal(runContext, response);
                response = finalizeRun(runContext, route, strategyResult, response);
            } finally {
                runRegistry.remove(runContext);
            }
        }
        return response;
    }

    private void synchronizeDurableTerminal(AgentRunContext context, AnalysisResponse response) {
        if (!durableRunStore.isEnabled() || !context.snapshot().status().isTerminal()) {
            return;
        }
        String resultSummary = response != null && response.isSuccess()
                ? sanitize(response.getResult())
                : null;
        if (!durableRunStore.synchronizeTerminal(context.runId(), context.snapshot(), resultSummary)) {
            LOGGER.warn("Agent Run 持久化终态未更新，可能已被并发操作处理: runId={}", context.runId());
        }
    }

    private AgentExecutionStrategy.Result executeStrategy(AgentRunContext context, AgentRunRoute route) {
        AgentExecutionStrategy strategy = strategies.get(route.mode());
        if (strategy == null) {
            throw new IllegalStateException("没有可用的 Agent 执行策略: " + route.mode());
        }
        return strategy.execute(context, route);
    }

    private AnalysisResponse finalizeRun(AgentRunContext context, AgentRunRoute route,
            AgentExecutionStrategy.Result strategyResult, AnalysisResponse response) {
        AgentRunSnapshot snapshot = context.snapshot();
        AnalysisResponse finalResponse = response == null
                ? AnalysisResponse.fail(safeDetail(snapshot, "Agent 运行未返回结果"))
                : response;
        if (StringUtils.hasText(finalResponse.getError())) {
            finalResponse.setError(sanitize(finalResponse.getError()));
        }
        removeHiddenReasoning(finalResponse);
        if (snapshot.status() == AgentRunStatus.WAITING_APPROVAL) {
            finalResponse.setSuccess(true);
            finalResponse.setResult(null);
            finalResponse.setError(null);
        } else if (snapshot.status() != AgentRunStatus.COMPLETED) {
            finalResponse.setSuccess(false);
            if (!StringUtils.hasText(finalResponse.getError())) {
                finalResponse.setError(safeDetail(snapshot, "Agent 运行未完成"));
            }
        }
        finalResponse.setOutcomeEvaluation(evaluateOutcome(context, finalResponse, snapshot));
        applyRunMetadata(context, snapshot, finalResponse);
        recordConversation(context, route, finalResponse, snapshot);
        recordTrace(context, route, finalResponse);
        try {
            if (snapshot.status() != AgentRunStatus.WAITING_APPROVAL) {
                emitFinalProcessEvents(context, strategyResult, finalResponse);
            }
        } catch (Exception e) {
            LOGGER.debug("Agent Run 过程事件已不可写: runId={}", context.runId());
        }
        try {
            if (snapshot.status().isTerminal()) {
                emitTerminal(context, finalResponse);
            } else if (snapshot.status() == AgentRunStatus.WAITING_APPROVAL) {
                emitPaused(context, finalResponse);
            }
        } catch (Exception e) {
            LOGGER.debug("Agent Run 终态事件已不可写: runId={}", context.runId());
        }
        LOGGER.info("Agent Run 当前执行段结束: runId={}, status={}, reason={}, durationMs={}, "
                        + "iterations={}, modelCalls={}, toolCalls={}, tokens={}",
                context.runId(), snapshot.status(), snapshot.terminationReason(), snapshot.durationMs(),
                snapshot.iterations(), snapshot.modelCalls(), snapshot.toolCalls(), snapshot.tokens());
        return finalResponse;
    }

    private AgentRunContext createRunContext(AgentExecutionContext executionContext, AgentRunRoute route,
            AgentEventSink eventSink) {
        AnalysisRequest request = executionContext.getRequest();
        ConversationSession session = executionContext.getSession();
        AgentRunControl control = new AgentRunControl(runtimeProperties.toLimits());
        AgentEventSink delegate = eventSink == null ? AgentEventSink.noop() : eventSink;
        AgentEventSink guardedSink = delegate instanceof GuardedAgentEventSink
                ? delegate
                : new GuardedAgentEventSink(delegate);
        String tenantId = securityContextHelper.getCurrentTenantId();
        String userId = securityContextHelper.getCurrentUserId();
        AgentToolAuthorizationSnapshot authorization = toolAuthorizationService.resolve(userId, tenantId);
        return new AgentRunContext(
                UUID.randomUUID().toString(),
                tenantId,
                userId,
                session != null ? session.getSessionId() : request.getSessionId(),
                route.profile() != null ? route.profile().getAgentId() : request.getAgentId(),
                route.mode(),
                executionContext,
                control,
                guardedSink,
                authorization,
                new AgentToolExecutionJournal(toolGovernanceProperties.getJournalCapacity()));
    }

    private void emitStarted(AgentRunContext context) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", AgentRunStatus.RUNNING.name());
        payload.put("origin", context.executionContext().getOrigin().name());
        payload.put("sessionId", nullToEmpty(context.sessionId()));
        payload.put("limits", context.control().getLimits());
        context.eventSink().emit(AgentEvent.of(context, AgentEventType.RUN_STARTED, payload));
    }

    private void emitRequestPlan(AgentRunContext context, AgentRunRoute route) {
        if (route.requestPlan() == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", "请求规划");
        payload.put("content", route.requestPlan().summary());
        payload.put("intent", route.requestPlan().intent().name());
        payload.put("matchedRule", route.requestPlan().matchedRule());
        payload.put("confidence", route.requestPlan().confidence());
        payload.put("modelRequired", route.requestPlan().modelRequired());
        payload.put("ragRequired", route.requestPlan().ragRequired());
        payload.put("memoryRequired", route.requestPlan().memoryRequired());
        payload.put("candidateToolCount", route.requestPlan().candidateTools().size());
        payload.put("toolSelectionFallback", route.requestPlan().toolSelectionFallback());
        context.eventSink().emit(AgentEvent.of(context, AgentEventType.EXECUTION_PLAN, payload));
    }

    private void emitFinalProcessEvents(AgentRunContext context, AgentExecutionStrategy.Result strategyResult,
            AnalysisResponse response) {
        if (!context.eventSink().isStreaming()) {
            return;
        }
        if (strategyResult != null && strategyResult.processEventsEmitted()) {
            if (!response.isSuccess()) {
                emit(context, AgentEventType.ERROR, Map.of("content", safeError(response)));
            }
            return;
        }
        emitThinkingSteps(context, response.getThinkingSteps());
        if (response.isSuccess()) {
            emit(context, AgentEventType.MODEL_TOKEN, Map.of("content", nullToEmpty(response.getResult())));
        } else {
            emit(context, AgentEventType.ERROR, Map.of("content", safeError(response)));
        }
    }

    private void emitThinkingSteps(AgentRunContext context, List<AnalysisResponse.ThinkingStep> steps) {
        if (steps == null) {
            return;
        }
        for (AnalysisResponse.ThinkingStep step : steps) {
            String type = nullToEmpty(step.getType());
            if (isHiddenReasoningType(type) || "tool_call".equals(type)) {
                continue;
            }
            String content = StringUtils.hasText(step.getToolResult()) ? step.getToolResult() : step.getContent();
            if (!StringUtils.hasText(content)) {
                continue;
            }
            if ("reflection".equals(type)) {
                emit(context, AgentEventType.REFLECTION, Map.of("content", content));
            } else if ("plan".equals(type)) {
                emit(context, AgentEventType.EXECUTION_PLAN, Map.of("title", "执行计划", "content", content));
            } else if ("parallel_precheck".equals(type)) {
                emit(context, AgentEventType.PARALLEL_PRECHECK, Map.of("title", "并行预检", "content", content));
            } else if ("orchestrator".equals(type) || "orchestrator_task".equals(type)) {
                String title = "orchestrator".equals(type) ? "编排决策" : "编排任务 " + step.getStep();
                emit(context, AgentEventType.ORCHESTRATION, Map.of("title", title, "content", content));
            }
        }
    }

    private void emitTerminal(AgentRunContext context, AnalysisResponse response) {
        AgentRunSnapshot snapshot = context.snapshot();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", snapshot.status().name());
        payload.put("terminationReason", snapshot.terminationReason().name());
        payload.put("detail", snapshot.detail());
        payload.put("usage", snapshot);
        payload.put("sessionId", nullToEmpty(response.getSessionId()));
        payload.put("traceId", nullToEmpty(response.getTraceId()));
        payload.put("outcomeStatus", response.getOutcomeStatus().name());
        payload.put("outcomeEvaluation", response.getOutcomeEvaluation());
        context.eventSink().emit(AgentEvent.of(context, AgentEventType.RUN_TERMINATED, payload));
    }

    private void emitPaused(AgentRunContext context, AnalysisResponse response) {
        AgentRunSnapshot snapshot = context.snapshot();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", AgentRunStatus.WAITING_APPROVAL.name());
        payload.put("businessCompleted", false);
        payload.put("approvalId", nullToEmpty(response.getApprovalId()));
        payload.put("sessionId", nullToEmpty(response.getSessionId()));
        payload.put("usage", snapshot);
        context.eventSink().emit(AgentEvent.of(context, AgentEventType.RUN_PAUSED, payload));
    }

    private void emit(AgentRunContext context, AgentEventType type, Map<String, Object> payload) {
        context.eventSink().emit(AgentEvent.of(context, type, payload));
    }

    private void applyRunMetadata(AgentRunContext context, AgentRunSnapshot snapshot, AnalysisResponse response) {
        response.setRunId(context.runId());
        response.setExecutionMode(context.mode().name());
        response.setRunStatus(snapshot.status().name());
        response.setTerminationReason(snapshot.terminationReason().name());
        response.setRunUsage(snapshot);
        response.setTokenConsumed(snapshot.tokens());
        if (!StringUtils.hasText(response.getSessionId())) {
            response.setSessionId(context.sessionId());
        }
    }

    private AgentOutcomeEvaluation evaluateOutcome(AgentRunContext context,
            AnalysisResponse response,
            AgentRunSnapshot snapshot) {
        List<AgentToolExecutionRecord> toolRecords = context.toolJournal().snapshot();
        Set<String> calledTools = toolRecords.stream()
                .filter(record -> record.status() == AgentToolExecutionStatus.SUCCESS)
                .map(AgentToolExecutionRecord::toolName)
                .filter(StringUtils::hasText)
                .collect(Collectors.toUnmodifiableSet());
        Set<String> approvalStatuses = toolRecords.stream()
                .filter(record -> record.status() == AgentToolExecutionStatus.APPROVAL_REQUIRED)
                .map(record -> record.status().name())
                .collect(Collectors.toUnmodifiableSet());
        boolean journalComplete = context.toolJournal().overflowCount() == 0L;
        return outcomeEvaluator.evaluate(
                context.taskContract(),
                new OutcomeEvidence(
                        response.getResult(),
                        snapshot.status(),
                        calledTools,
                        approvalStatuses,
                        journalComplete,
                        journalComplete));
    }

    private void recordConversation(AgentRunContext context, AgentRunRoute route, AnalysisResponse response,
            AgentRunSnapshot snapshot) {
        if (!context.executionContext().getOrigin().isConversationPersistenceEnabled()) {
            return;
        }
        ConversationSession session = context.executionContext().getSession();
        AnalysisRequest request = context.executionContext().getRequest();
        String modelId = request.hasModel() ? request.getModelId() : null;
        if (snapshot.status() == AgentRunStatus.WAITING_APPROVAL) {
            conversationRecorder.recordWaitingApprovalConversation(
                    session, request, response.getSkillUsed(), modelId, context.runId());
            return;
        }
        if (snapshot.status() != AgentRunStatus.COMPLETED || !response.isSuccess()) {
            return;
        }
        if (route.target() == AgentRunRoute.Target.COMMAND || route.target() == AgentRunRoute.Target.SKILL) {
            conversationRecorder.recordSessionConversation(session, request, response.getResult(),
                    response.getSkillUsed(), modelId, context.runId());
        } else {
            conversationRecorder.recordAnalysisConversation(session, request, response.getResult(),
                    response.getSkillUsed(), modelId, context.runId());
        }
    }

    private void recordTrace(AgentRunContext context, AgentRunRoute route, AnalysisResponse response) {
        if (traceService == null) {
            return;
        }
        try {
            traceService.recordRun(context, route, response);
            response.setTraceId(context.runId());
        } catch (Exception e) {
            LOGGER.warn("记录 Agent Run 轨迹失败: runId={}, error={}", context.runId(), e.getMessage());
        }
    }

    private Map<AgentExecutionMode, AgentExecutionStrategy> indexStrategies(
            List<AgentExecutionStrategy> executionStrategies) {
        Map<AgentExecutionMode, AgentExecutionStrategy> indexed = new EnumMap<>(AgentExecutionMode.class);
        for (AgentExecutionStrategy strategy : executionStrategies) {
            AgentExecutionStrategy previous = indexed.put(strategy.mode(), strategy);
            if (previous != null) {
                throw new IllegalStateException("Agent 执行策略重复: " + strategy.mode());
            }
        }
        return Map.copyOf(indexed);
    }

    private String safeError(AnalysisResponse response) {
        return StringUtils.hasText(response.getError()) ? sanitize(response.getError()) : "分析失败";
    }

    private String safeMessage(Exception exception) {
        return StringUtils.hasText(exception.getMessage()) ? sanitize(exception.getMessage()) : "Agent 运行失败";
    }

    private String safeDetail(AgentRunSnapshot snapshot, String fallback) {
        return snapshot != null && StringUtils.hasText(snapshot.detail()) ? snapshot.detail() : fallback;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private void removeHiddenReasoning(AnalysisResponse response) {
        if (response.getThinkingSteps() == null) {
            return;
        }
        response.setThinkingSteps(response.getThinkingSteps().stream()
                .filter(step -> step != null && !isHiddenReasoningType(step.getType()))
                .toList());
    }

    private boolean isHiddenReasoningType(String type) {
        return "thinking".equalsIgnoreCase(nullToEmpty(type))
                || "thought".equalsIgnoreCase(nullToEmpty(type))
                || "chain_of_thought".equalsIgnoreCase(nullToEmpty(type));
    }

    private String sanitize(String value) {
        return value == null ? "" : SECRET_PATTERN.matcher(value).replaceAll("$1***");
    }
}
