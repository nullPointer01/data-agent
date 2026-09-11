package com.ai.agent.runtime;

import com.ai.agent.durable.AgentDurableRunStore;
import com.ai.agent.durable.AgentRunStateEntity;
import com.ai.agent.outcome.AgentOutcomeEvaluation;
import com.ai.agent.runtime.dto.AgentRunEvidenceResponse;
import com.ai.agent.runtime.dto.AgentRunEvidenceResponse.RunEvent;
import com.ai.agent.runtime.dto.AgentRunEvidenceResponse.RunUsage;
import com.ai.model.AgentExecutionTrace;
import com.ai.repository.AgentExecutionTraceRepository;
import com.ai.security.SecurityContextHelper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.ZoneId;
import java.util.*;

/**
 * 合并 durable 状态和 Trace，为 Run 所有者提供安全证据投影。
 *
 * @author data-agent
 */
@Service
public class AgentRunEvidenceService {

    private static final String NOT_FOUND_OR_DENIED = "运行不存在或无权限";

    private final AgentDurableRunStore durableRunStore;
    private final AgentExecutionTraceRepository traceRepository;
    private final SecurityContextHelper securityContextHelper;
    private final ObjectMapper objectMapper;

    public AgentRunEvidenceService(AgentDurableRunStore durableRunStore,
            AgentExecutionTraceRepository traceRepository,
            SecurityContextHelper securityContextHelper,
            ObjectMapper objectMapper) {
        this.durableRunStore = durableRunStore;
        this.traceRepository = traceRepository;
        this.securityContextHelper = securityContextHelper;
        this.objectMapper = objectMapper;
    }

    /**
     * 查询当前认证用户拥有的 Run 证据。
     *
     * @param runId Run 编号
     * @return 安全 Run 证据
     */
    @Transactional(readOnly = true)
    public AgentRunEvidenceResponse getOwned(String runId) {
        if (!StringUtils.hasText(runId)) {
            throw new IllegalArgumentException(NOT_FOUND_OR_DENIED);
        }
        String tenantId = securityContextHelper.getCurrentTenantId();
        String userId = securityContextHelper.getCurrentUserId();
        Optional<AgentRunStateEntity> durable = durableRunStore.findOwned(runId, tenantId, userId);
        Optional<AgentExecutionTrace> trace = traceRepository.findByTraceIdAndTenantId(runId, tenantId)
                .filter(item -> userId.equals(item.getUserId()));
        if (durable.isEmpty() && trace.isEmpty()) {
            throw new IllegalArgumentException(NOT_FOUND_OR_DENIED);
        }
        return buildResponse(runId, durable.orElse(null), trace.orElse(null));
    }

    private AgentRunEvidenceResponse buildResponse(String runId,
            AgentRunStateEntity durable,
            AgentExecutionTrace trace) {
        JsonNode runMetadata = readRunMetadata(trace);
        String status = durable == null
                ? text(runMetadata, "status", trace != null && trace.isSuccess() ? "COMPLETED" : "FAILED")
                : durable.getStatus().name();
        String terminationReason = durable == null
                ? text(runMetadata, "terminationReason", trace != null && trace.isSuccess() ? "COMPLETED" : "FAILED")
                : durable.getTerminationReason().name();
        long durationMs = durable == null
                ? number(runMetadata, "durationMs", trace == null ? 0L : trace.getDurationMs())
                : resolveDurableDuration(durable);
        int iterations = durable == null ? integer(runMetadata, "iterations", 0) : durable.getUsedIterations();
        int modelCalls = durable == null ? integer(runMetadata, "modelCalls", 0) : durable.getUsedModelCalls();
        int toolCalls = durable == null ? integer(runMetadata, "toolCalls", 0) : durable.getUsedToolCalls();
        long tokens = durable == null ? number(runMetadata, "tokens", 0L) : durable.getUsedTokens();
        boolean estimated = durable == null
                ? bool(runMetadata, "tokenUsageEstimated", false)
                : durable.isTokenUsageEstimated();
        long remainingTimeout = durable == null
                ? number(runMetadata, "remainingActiveTimeoutMs", 0L)
                : durable.getRemainingActiveTimeoutMs();
        Instant completedAt = durable != null && durable.getCompletedAt() != null
                ? durable.getCompletedAt() : traceTime(trace);
        Instant startedAt = durable != null && durable.getCreatedAt() != null
                ? durable.getCreatedAt() : subtractDuration(completedAt, durationMs);
        Instant updatedAt = durable != null ? durable.getUpdatedAt() : completedAt;
        String mode = durable != null && durable.getMode() != null
                ? durable.getMode().name() : trace == null ? "" : trace.getSelectedType();
        List<RunEvent> events = buildEvents(trace, durable, startedAt, completedAt, status, terminationReason);
        JsonNode shared = readJson(trace == null ? null : trace.getSharedContextJson());
        AgentOutcomeEvaluation outcomeEvaluation = readOutcomeEvaluation(shared.path("outcomeEvaluation"));
        RunUsage usage = new RunUsage(durationMs, iterations, modelCalls, toolCalls, tokens, estimated,
                remainingTimeout);

        return new AgentRunEvidenceResponse(
                runId,
                trace == null ? null : trace.getTraceId(),
                durable != null ? durable.getSessionId() : trace == null ? null : trace.getSessionId(),
                durable == null ? null : durable.getAgentId(),
                trace == null ? null : trace.getSelectedAgent(),
                mode,
                status,
                terminationReason,
                durable == null ? statusDetail(status) : safeDetail(durable.getErrorSummary(), status),
                durable == null ? null : durable.getApprovalId(),
                durable == null ? null : durable.getApprovalExpiresAt(),
                iterations,
                modelCalls,
                toolCalls,
                tokens,
                estimated,
                remainingTimeout,
                durationMs,
                usage,
                durable == null ? null : durable.getResultSummary(),
                startedAt,
                startedAt,
                updatedAt,
                completedAt,
                evidenceSource(durable, trace),
                false,
                shared.path("taskContract").path("present").asBoolean(false),
                outcomeEvaluation,
                events);
    }

    private List<RunEvent> buildEvents(AgentExecutionTrace trace,
            AgentRunStateEntity durable,
            Instant startedAt,
            Instant completedAt,
            String status,
            String terminationReason) {
        List<RunEvent> events = new ArrayList<>();
        events.add(new RunEvent("run_started", startedAt, "RUNNING", "Run 开始",
                "Harness 已创建运行并固化执行边界", null, null));
        JsonNode plan = readJson(trace == null ? null : trace.getPlanJson()).path("requestPlan");
        appendRequestPlanEvent(events, plan, startedAt);
        JsonNode shared = readJson(trace == null ? null : trace.getSharedContextJson());
        appendContextEvents(events, shared.path("contextGovernance").path("events"));
        appendToolEvents(events, shared.path("toolGovernance").path("records"));
        appendResumeEvents(events, shared.path("durableResumes"));
        if (durable != null && StringUtils.hasText(durable.getApprovalId())
                && (durable.getStatus() == AgentRunStatus.WAITING_APPROVAL
                || durable.getStatus() == AgentRunStatus.RESUMING)) {
            events.add(new RunEvent("approval_required", durable.getUpdatedAt(), durable.getStatus().name(),
                    "等待动作审批", "运行已安全暂停，审批后从持久化状态恢复",
                    durable.getApprovalId(), null));
        }
        if (completedAt != null && isTerminalStatus(status)) {
            String type = "COMPLETED".equals(status) ? "done" : "error";
            events.add(new RunEvent(type, completedAt, status, "Run " + statusDetail(status),
                    "终止原因：" + terminationReason, null, null));
        }
        events.sort(Comparator.comparing(RunEvent::eventTime,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return List.copyOf(events);
    }

    private void appendRequestPlanEvent(List<RunEvent> events, JsonNode plan, Instant startedAt) {
        if (plan == null || !plan.isObject()) {
            return;
        }
        String intent = text(plan, "intent", "UNKNOWN");
        String mode = text(plan, "mode", "CHAT");
        boolean modelRequired = bool(plan, "modelRequired", true);
        boolean ragRequired = bool(plan, "ragRequired", false);
        boolean memoryRequired = bool(plan, "memoryRequired", false);
        int candidateToolCount = integer(plan, "candidateToolCount", 0);
        boolean fallback = bool(plan, "toolSelectionFallback", false);
        String reason = text(plan, "reason", "已完成请求规划");
        String summary = intent + " -> " + mode
                + "；模型=" + yesNo(modelRequired)
                + "，知识库=" + yesNo(ragRequired)
                + "，记忆=" + yesNo(memoryRequired)
                + "，候选工具=" + (fallback ? "绑定能力兜底" : candidateToolCount + " 个")
                + "；原因：" + reason;
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("plannerVersion", text(plan, "plannerVersion", "unknown"));
        details.put("intent", intent);
        details.put("mode", mode);
        details.put("matchedRule", text(plan, "matchedRule", "unknown"));
        details.put("confidence", plan.path("confidence").asDouble(0.0D));
        details.put("modelRequired", modelRequired);
        details.put("ragRequired", ragRequired);
        details.put("memoryRequired", memoryRequired);
        details.put("candidateToolCount", candidateToolCount);
        details.put("toolSelectionFallback", fallback);
        events.add(new RunEvent("execution_plan", startedAt, mode,
                "请求规划", summary, null, details));
    }

    private void appendContextEvents(List<RunEvent> events, JsonNode contextEvents) {
        if (!contextEvents.isArray()) {
            return;
        }
        for (JsonNode item : contextEvents) {
            String decision = text(item, "decision", "ADMITTED");
            long before = number(item, "estimatedTokensBefore", 0L);
            long after = number(item, "estimatedTokensAfter", before);
            int retained = integer(item, "retainedMessageCount", 0);
            int original = integer(item, "originalMessageCount", retained);
            String title = switch (decision) {
                case "REDUCED" -> "上下文已压缩";
                case "REJECTED" -> "上下文被拒绝";
                default -> "上下文未压缩";
            };
            String summary = "Token " + before + " -> " + after
                    + "，保留 " + retained + "/" + original + " 条消息";
            Map<String, Object> details = new java.util.LinkedHashMap<>();
            details.put("decision", decision);
            details.put("reason", text(item, "reason", "WITHIN_BUDGET"));
            details.put("strategy", text(item, "strategy", "NONE"));
            details.put("estimatedTokensBefore", before);
            details.put("estimatedTokensAfter", after);
            details.put("originalMessageCount", original);
            details.put("retainedMessageCount", retained);
            details.put("protectedMessageCount", integer(item, "protectedMessageCount", 0));
            details.put("modelWindowTokens", number(item, "modelWindowTokens", 0L));
            details.put("runRemainingTokens", number(item, "runRemainingTokens", 0L));
            details.put("inputTokenLimit", number(item, "inputTokenLimit", 0L));
            details.put("tokenEstimateUsed", bool(item, "tokenEstimateUsed", true));
            events.add(new RunEvent("context_governed", instant(item, "occurredAt"), decision,
                    title, summary, null, details));
        }
    }

    private void appendToolEvents(List<RunEvent> events, JsonNode records) {
        if (!records.isArray()) {
            return;
        }
        for (JsonNode item : records) {
            String toolName = text(item, "toolName", "未知工具");
            String toolStatus = text(item, "status", "UNKNOWN");
            String risk = text(item, "risk", "UNKNOWN");
            long durationMs = number(item, "durationMs", 0L);
            int attempts = integer(item, "attempts", 0);
            String summary = "状态 " + toolStatus + "，风险 " + risk + "，尝试 " + attempts
                    + " 次，耗时 " + durationMs + " ms";
            events.add(new RunEvent("tool_call", instant(item, "occurredAt"), toolStatus,
                    "工具：" + toolName, summary, null, null));
        }
    }

    private void appendResumeEvents(List<RunEvent> events, JsonNode resumes) {
        if (!resumes.isArray()) {
            return;
        }
        for (JsonNode item : resumes) {
            String outcome = text(item, "outcome", "UNKNOWN");
            String title = switch (outcome) {
                case "STARTED" -> "恢复任务已领取";
                case "SUCCEEDED" -> "恢复执行完成";
                case "SUSPENDED_AGAIN" -> "运行再次等待审批";
                default -> "恢复执行失败";
            };
            events.add(new RunEvent("resume", instant(item, "recordedAt"), outcome,
                    title, safeDetail(text(item, "detail", ""), outcome),
                    text(item, "approvalId", null), null));
        }
    }

    private JsonNode readRunMetadata(AgentExecutionTrace trace) {
        if (trace == null) {
            return objectMapper.createObjectNode();
        }
        JsonNode sharedRun = readJson(trace.getSharedContextJson()).path("run");
        if (sharedRun.isObject()) {
            return sharedRun;
        }
        JsonNode plan = readJson(trace.getPlanJson());
        return plan.isObject() ? plan : objectMapper.createObjectNode();
    }

    private JsonNode readJson(String value) {
        if (!StringUtils.hasText(value)) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode parsed = objectMapper.readTree(value);
            return parsed == null ? objectMapper.createObjectNode() : parsed;
        } catch (Exception ignored) {
            return objectMapper.createObjectNode();
        }
    }

    private AgentOutcomeEvaluation readOutcomeEvaluation(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        try {
            return objectMapper.treeToValue(node, AgentOutcomeEvaluation.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.path(field);
        return value.isTextual() && StringUtils.hasText(value.asText()) ? value.asText() : fallback;
    }

    private long number(JsonNode node, String field, long fallback) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.asLong() : fallback;
    }

    private int integer(JsonNode node, String field, int fallback) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.asInt() : fallback;
    }

    private boolean bool(JsonNode node, String field, boolean fallback) {
        JsonNode value = node.path(field);
        return value.isBoolean() ? value.asBoolean() : fallback;
    }

    private String yesNo(boolean value) {
        return value ? "是" : "否";
    }

    private Instant instant(JsonNode node, String field) {
        String value = text(node, field, null);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Instant traceTime(AgentExecutionTrace trace) {
        return trace == null || trace.getCreatedAt() == null
                ? null : trace.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant();
    }

    private Instant subtractDuration(Instant completedAt, long durationMs) {
        return completedAt == null ? null : completedAt.minusMillis(Math.max(0L, durationMs));
    }

    private long resolveDurableDuration(AgentRunStateEntity entity) {
        // 审批等待不属于 active execution，不能用创建至更新时间冒充模型执行耗时。
        return Math.max(0L, entity.getTimeoutMs() - entity.getRemainingActiveTimeoutMs());
    }

    private String statusDetail(String status) {
        return switch (status) {
            case "COMPLETED" -> "已完成";
            case "WAITING_APPROVAL" -> "等待审批";
            case "RESUMING" -> "恢复中";
            case "CANCELLED" -> "已取消";
            case "TIMED_OUT" -> "已超时";
            case "BUDGET_EXHAUSTED" -> "预算已耗尽";
            case "REJECTED" -> "已拒绝";
            case "EXPIRED" -> "已过期";
            case "FAILED" -> "失败";
            default -> "运行中";
        };
    }

    private boolean isTerminalStatus(String status) {
        return "COMPLETED".equals(status)
                || "FAILED".equals(status)
                || "CANCELLED".equals(status)
                || "TIMED_OUT".equals(status)
                || "BUDGET_EXHAUSTED".equals(status)
                || "REJECTED".equals(status)
                || "EXPIRED".equals(status);
    }

    private String safeDetail(String detail, String status) {
        return StringUtils.hasText(detail) ? detail : statusDetail(status);
    }

    private String evidenceSource(AgentRunStateEntity durable, AgentExecutionTrace trace) {
        if (durable != null && trace != null) {
            return "DURABLE_AND_TRACE";
        }
        return durable != null ? "DURABLE" : "TRACE";
    }
}
