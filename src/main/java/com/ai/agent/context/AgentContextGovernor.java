package com.ai.agent.context;

import com.ai.agent.outcome.AgentSuccessCriterion;
import com.ai.agent.outcome.AgentTaskContract;
import com.ai.agent.runtime.AgentRunContext;
import com.ai.agent.runtime.AgentRunScope;
import com.ai.agent.runtime.AgentRunSnapshot;
import com.ai.agent.runtime.event.AgentEvent;
import com.ai.agent.runtime.event.AgentEventType;
import com.ai.agent.context.AgentContextEvidence.Decision;
import com.ai.agent.context.AgentContextEvidence.Reason;
import com.ai.agent.context.AgentContextPlan.MessageKind;
import com.ai.agent.context.AgentContextPlan.PlannedMessage;
import com.ai.agent.context.AgentContextPlan.ProtectionReason;
import com.ai.mcp.TokenMonitor;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

/**
 * 所有 Agent 模型调用共享的上下文准入边界。
 *
 * @author data-agent
 */
@Component
public class AgentContextGovernor {

    private static final String NO_REDUCTION = "NONE";
    private static final String DISABLED_REDUCTION = "DISABLED";
    private static final int MAX_TRACKED_RUNS = 1_000;
    private static final int MAX_EVIDENCE_PER_RUN = 32;

    private final AgentContextProperties properties;
    private final TokenMonitor tokenMonitor;
    private final ContextReductionStrategy reductionStrategy;
    private final Map<String, List<ContextEvidenceRecord>> evidenceByRun = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, List<ContextEvidenceRecord>> eldest) {
                    return size() > MAX_TRACKED_RUNS;
                }
            });

    public AgentContextGovernor(AgentContextProperties properties,
            TokenMonitor tokenMonitor,
            ConservativeContextReducer reductionStrategy) {
        this.properties = properties;
        this.tokenMonitor = tokenMonitor;
        this.reductionStrategy = reductionStrategy;
    }

    /**
     * 生成计划并执行准入或保守裁剪。
     *
     * @param sourceMessages 调用方准备的完整消息
     * @param toolSpecifications 本次调用可用工具定义
     * @param modelId 项目模型 ID
     * @return 可直接发送给模型的上下文与安全证据
     * @throws ContextLimitException protected 内容无法安全放入预算时抛出
     */
    public GovernedContext govern(List<ChatMessage> sourceMessages,
            List<ToolSpecification> toolSpecifications,
            String modelId) {
        List<ChatMessage> messages = withTaskContract(sourceMessages);
        AgentContextPlan plan = createPlan(messages, toolSpecifications, modelId);
        if (plan.estimatedInputTokens() <= plan.inputTokenLimit()) {
            return publish(admitted(messages, plan));
        }
        if (!plan.compactionEnabled()) {
            throw publish(rejected(plan, Reason.COMPACTION_DISABLED, DISABLED_REDUCTION,
                    plan.estimatedInputTokens(), messages.size()));
        }

        ContextReductionStrategy.ReductionResult reduced = reductionStrategy.reduce(messages, plan);
        long reducedInputTokens = reduced.estimatedMessageTokens() + plan.estimatedToolTokens();
        if (!retainsProtectedMessages(plan, messages, reduced.messages())) {
            throw publish(rejected(plan, Reason.PROTECTED_CONTEXT_EXCEEDS_LIMIT, reductionStrategy.strategyId(),
                    reducedInputTokens, reduced.messages().size()));
        }
        if (reducedInputTokens > plan.inputTokenLimit()) {
            Reason reason = plan.protectedMessageTokens() + plan.estimatedToolTokens() > plan.inputTokenLimit()
                    ? Reason.PROTECTED_CONTEXT_EXCEEDS_LIMIT : Reason.REDUCTION_INSUFFICIENT;
            throw publish(rejected(plan, reason, reductionStrategy.strategyId(),
                    reducedInputTokens, reduced.messages().size()));
        }

        AgentContextEvidence evidence = evidence(plan, Decision.REDUCED,
                Reason.CONSERVATIVE_TRIM_APPLIED, reductionStrategy.strategyId(),
                reducedInputTokens, reduced.messages().size());
        return publish(new GovernedContext(reduced.messages(), plan, evidence));
    }

    /**
     * 读取一次 Run 已产生的有界上下文证据，供 Trace 安全持久化。
     *
     * @param runId Run 编号
     * @return 按发生顺序排列的证据
     */
    public List<ContextEvidenceRecord> evidenceForRun(String runId) {
        if (runId == null || runId.isBlank()) {
            return List.of();
        }
        synchronized (evidenceByRun) {
            return List.copyOf(evidenceByRun.getOrDefault(runId, List.of()));
        }
    }

    private GovernedContext publish(GovernedContext governedContext) {
        AgentRunContext runContext = AgentRunScope.current().orElse(null);
        if (runContext == null) {
            return governedContext;
        }
        record(runContext, governedContext.evidence());
        runContext.eventSink().emit(AgentEvent.of(runContext, AgentEventType.CONTEXT_GOVERNED,
                Map.of("evidence", governedContext.evidence())));
        return governedContext;
    }

    private ContextLimitException publish(ContextLimitException exception) {
        AgentRunContext runContext = AgentRunScope.current().orElse(null);
        if (runContext == null) {
            return exception;
        }
        record(runContext, exception.getEvidence());
        runContext.eventSink().emit(AgentEvent.of(runContext, AgentEventType.CONTEXT_GOVERNED,
                Map.of("evidence", exception.getEvidence())));
        return exception;
    }

    private void record(AgentRunContext runContext, AgentContextEvidence evidence) {
        synchronized (evidenceByRun) {
            List<ContextEvidenceRecord> records = new ArrayList<>(
                    evidenceByRun.getOrDefault(runContext.runId(), List.of()));
            records.add(new ContextEvidenceRecord(Instant.now(), evidence));
            if (records.size() > MAX_EVIDENCE_PER_RUN) {
                records = new ArrayList<>(records.subList(records.size() - MAX_EVIDENCE_PER_RUN, records.size()));
            }
            evidenceByRun.put(runContext.runId(), List.copyOf(records));
        }
    }

    private AgentContextPlan createPlan(List<ChatMessage> messages,
            List<ToolSpecification> toolSpecifications,
            String modelId) {
        AgentRunContext runContext = AgentRunScope.current().orElse(null);
        long modelWindow = properties.resolveModelWindowTokens(modelId);
        long runRemaining = runRemainingTokens(runContext, modelWindow);
        long modelInputLimit = Math.max(0L, modelWindow
                - properties.getReservedOutputTokens()
                - properties.getSafetyMarginTokens());
        long runInputLimit = Math.max(0L, runRemaining - properties.getReservedOutputTokens());
        long inputLimit = Math.min(modelInputLimit, runInputLimit);
        List<PlannedMessage> plannedMessages = planMessages(messages);
        long messageTokens = plannedMessages.stream().mapToLong(PlannedMessage::estimatedTokens).sum();
        long toolTokens = estimateToolTokens(toolSpecifications);
        return new AgentContextPlan(
                plannedMessages,
                messageTokens,
                toolTokens,
                modelWindow,
                runRemaining,
                inputLimit,
                properties.getReservedOutputTokens(),
                properties.getSafetyMarginTokens(),
                properties.getCompaction().isEnabled());
    }

    private List<PlannedMessage> planMessages(List<ChatMessage> messages) {
        List<Set<ProtectionReason>> protections = new ArrayList<>(messages.size());
        for (int index = 0; index < messages.size(); index++) {
            protections.add(EnumSet.noneOf(ProtectionReason.class));
        }
        protectSystemMessages(messages, protections);
        protectCurrentTask(messages, protections);
        protectRecentMessages(messages, protections);
        protectUnresolvedToolProtocol(messages, protections);

        List<PlannedMessage> planned = new ArrayList<>(messages.size());
        for (int index = 0; index < messages.size(); index++) {
            ChatMessage message = messages.get(index);
            planned.add(new PlannedMessage(index, messageKind(message),
                    tokenMonitor.estimateTokens(extractMessageText(message)), protections.get(index)));
        }
        return List.copyOf(planned);
    }

    private void protectSystemMessages(List<ChatMessage> messages,
            List<Set<ProtectionReason>> protections) {
        for (int index = 0; index < messages.size(); index++) {
            if (messages.get(index) instanceof SystemMessage systemMessage) {
                protections.get(index).add(ProtectionReason.SYSTEM_INSTRUCTION);
                if (systemMessage.text().startsWith("## 当前任务合同")) {
                    protections.get(index).add(ProtectionReason.TASK_CONTRACT);
                }
            }
        }
    }

    private void protectCurrentTask(List<ChatMessage> messages,
            List<Set<ProtectionReason>> protections) {
        for (int index = messages.size() - 1; index >= 0; index--) {
            if (messages.get(index) instanceof UserMessage) {
                protections.get(index).add(ProtectionReason.CURRENT_TASK);
                return;
            }
        }
    }

    private void protectRecentMessages(List<ChatMessage> messages,
            List<Set<ProtectionReason>> protections) {
        int firstProtected = Math.max(0, messages.size() - properties.getProtectedRecentMessages());
        for (int index = firstProtected; index < messages.size(); index++) {
            protections.get(index).add(ProtectionReason.RECENT_EVIDENCE);
        }
    }

    private void protectUnresolvedToolProtocol(List<ChatMessage> messages,
            List<Set<ProtectionReason>> protections) {
        Map<String, Integer> unresolvedOwners = new HashMap<>();
        for (int index = 0; index < messages.size(); index++) {
            ChatMessage message = messages.get(index);
            if (message instanceof AiMessage assistant && assistant.hasToolExecutionRequests()) {
                for (ToolExecutionRequest request : assistant.toolExecutionRequests()) {
                    unresolvedOwners.put(request.id(), index);
                }
            } else if (message instanceof ToolExecutionResultMessage result) {
                unresolvedOwners.remove(result.id());
            }
        }
        for (Integer ownerIndex : unresolvedOwners.values()) {
            protections.get(ownerIndex).add(ProtectionReason.UNRESOLVED_TOOL_PROTOCOL);
            protections.get(ownerIndex).add(ProtectionReason.ACTIVE_APPROVAL);
        }
    }

    private List<ChatMessage> withTaskContract(List<ChatMessage> sourceMessages) {
        List<ChatMessage> messages = sourceMessages == null
                ? new ArrayList<>() : new ArrayList<>(sourceMessages);
        AgentTaskContract taskContract = AgentRunScope.current()
                .map(AgentRunContext::taskContract)
                .orElse(null);
        if (taskContract == null) {
            return List.copyOf(messages);
        }
        messages.add(0, SystemMessage.from(formatTaskContract(taskContract)));
        return List.copyOf(messages);
    }

    private String formatTaskContract(AgentTaskContract taskContract) {
        StringBuilder builder = new StringBuilder("## 当前任务合同\n目标: ")
                .append(taskContract.goal())
                .append("\n成功标准:");
        for (AgentSuccessCriterion criterion : taskContract.criteria()) {
            builder.append("\n- [")
                    .append(criterion.criterionId())
                    .append("] ")
                    .append(criterion.type())
                    .append(" = ")
                    .append(criterion.expectedValue());
        }
        return builder.toString();
    }

    private long runRemainingTokens(AgentRunContext context, long modelWindow) {
        if (context == null) {
            return modelWindow;
        }
        AgentRunSnapshot snapshot = context.snapshot();
        return Math.max(0L, context.control().getLimits().maxTokens() - snapshot.tokens());
    }

    private long estimateToolTokens(List<ToolSpecification> toolSpecifications) {
        if (toolSpecifications == null || toolSpecifications.isEmpty()) {
            return 0L;
        }
        return toolSpecifications.stream()
                .map(String::valueOf)
                .mapToLong(tokenMonitor::estimateTokens)
                .sum();
    }

    private boolean retainsProtectedMessages(AgentContextPlan plan,
            List<ChatMessage> original,
            List<ChatMessage> retained) {
        Map<ChatMessage, Integer> retainedCounts = new HashMap<>();
        retained.forEach(message -> retainedCounts.merge(message, 1, Integer::sum));
        for (PlannedMessage plannedMessage : plan.messages()) {
            if (!plannedMessage.protectedMessage()) {
                continue;
            }
            ChatMessage message = original.get(plannedMessage.index());
            Integer count = retainedCounts.get(message);
            if (count == null || count == 0) {
                return false;
            }
            retainedCounts.put(message, count - 1);
        }
        return true;
    }

    private GovernedContext admitted(List<ChatMessage> messages, AgentContextPlan plan) {
        AgentContextEvidence evidence = evidence(plan, Decision.ADMITTED, Reason.WITHIN_BUDGET,
                NO_REDUCTION, plan.estimatedInputTokens(), messages.size());
        return new GovernedContext(messages, plan, evidence);
    }

    private ContextLimitException rejected(AgentContextPlan plan, Reason reason, String strategy,
            long estimatedTokensAfter, int retainedMessageCount) {
        AgentContextEvidence evidence = evidence(plan, Decision.REJECTED, reason, strategy,
                estimatedTokensAfter, retainedMessageCount);
        return new ContextLimitException("AGENT_CONTEXT_" + reason.name(), evidence);
    }

    private AgentContextEvidence evidence(AgentContextPlan plan, Decision decision, Reason reason,
            String strategy, long estimatedTokensAfter, int retainedMessageCount) {
        int protectedCount = (int) plan.messages().stream().filter(PlannedMessage::protectedMessage).count();
        return new AgentContextEvidence(
                decision,
                reason,
                strategy,
                plan.estimatedInputTokens(),
                estimatedTokensAfter,
                plan.messages().size(),
                retainedMessageCount,
                protectedCount,
                plan.modelWindowTokens(),
                plan.runRemainingTokens(),
                plan.inputTokenLimit(),
                true);
    }

    private MessageKind messageKind(ChatMessage message) {
        if (message instanceof SystemMessage) {
            return MessageKind.SYSTEM;
        }
        if (message instanceof UserMessage) {
            return MessageKind.USER;
        }
        if (message instanceof AiMessage) {
            return MessageKind.ASSISTANT;
        }
        if (message instanceof ToolExecutionResultMessage) {
            return MessageKind.TOOL_RESULT;
        }
        return MessageKind.OTHER;
    }

    private String extractMessageText(ChatMessage message) {
        if (message instanceof UserMessage userMessage) {
            return userMessage.contents().stream()
                    .filter(TextContent.class::isInstance)
                    .map(TextContent.class::cast)
                    .map(TextContent::text)
                    .collect(Collectors.joining("\n"));
        }
        if (message instanceof SystemMessage systemMessage) {
            return systemMessage.text();
        }
        if (message instanceof AiMessage aiMessage) {
            String text = aiMessage.text() == null ? "" : aiMessage.text();
            return text + aiMessage.toolExecutionRequests();
        }
        if (message instanceof ToolExecutionResultMessage toolResultMessage) {
            return toolResultMessage.text();
        }
        return String.valueOf(message);
    }

    /**
     * 上下文准入后的不可变调用数据。
     *
     * @param messages 可发送给模型的消息
     * @param plan 原始预算计划
     * @param evidence 安全决策证据
     */
    public record GovernedContext(
            List<ChatMessage> messages,
            AgentContextPlan plan,
            AgentContextEvidence evidence) {

        public GovernedContext {
            messages = messages == null ? List.of() : List.copyOf(messages);
            if (plan == null || evidence == null) {
                throw new IllegalArgumentException("上下文计划与证据不能为空");
            }
        }
    }

    /**
     * 带时间的安全上下文证据，不包含消息正文。
     *
     * @param occurredAt 决策时间
     * @param evidence 上下文决策
     */
    public record ContextEvidenceRecord(Instant occurredAt, AgentContextEvidence evidence) {

        public ContextEvidenceRecord {
            if (occurredAt == null || evidence == null) {
                throw new IllegalArgumentException("上下文证据时间和内容不能为空");
            }
        }
    }

    /** 上下文无法安全准入时抛出的稳定异常。 */
    public static class ContextLimitException extends RuntimeException {

        private final AgentContextEvidence evidence;

        public ContextLimitException(String reasonCode, AgentContextEvidence evidence) {
            super(reasonCode);
            this.evidence = evidence;
        }

        public AgentContextEvidence getEvidence() {
            return evidence;
        }
    }
}
