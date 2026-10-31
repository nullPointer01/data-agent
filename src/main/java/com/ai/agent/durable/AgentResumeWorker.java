package com.ai.agent.durable;

import com.ai.indexing.RagIndexMaintenance;
import com.ai.background.BackgroundTaskConfiguration;
import com.ai.background.BackgroundUserScope;
import com.ai.agent.agentic.AgenticSupervisorService;
import com.ai.agent.approval.AgentApprovalDecisionStatus;
import com.ai.agent.approval.AgentApprovalResumeSignals;
import com.ai.agent.capability.AgentCapabilityBindingSnapshot;
import com.ai.agent.capability.AgentCapabilityScope;
import com.ai.agent.capability.AgentCapabilityService;
import com.ai.agent.approval.AgentApprovalExecutionStatus;
import com.ai.agent.approval.AgentApprovalReauthorization;
import com.ai.agent.approval.AgentApprovalReauthorizationService;
import com.ai.agent.approval.AgentApprovalTelemetry;
import com.ai.agent.approval.AgentToolApprovalEntity;
import com.ai.agent.approval.AgentToolApprovalRepository;
import com.ai.agent.outcome.AgentOutcomeEvaluation;
import com.ai.agent.outcome.AgentOutcomeEvaluator;
import com.ai.agent.outcome.AgentOutcomeEvaluator.OutcomeEvidence;
import com.ai.agent.outcome.AgentTaskContract;
import com.ai.agent.react.ReActExecutionResult;
import com.ai.agent.runtime.AgentRunContext;
import com.ai.agent.runtime.AgentRunRegistry;
import com.ai.agent.runtime.AgentRunScope;
import com.ai.agent.runtime.AgentRunStatus;
import com.ai.agent.runtime.AgentRunTerminationReason;
import com.ai.agent.tool.AgentConversationRecorder;
import com.ai.agent.tool.AgentToolInvoker;
import com.ai.agent.tool.governance.AgentToolExecutionResult;
import com.ai.agent.tool.governance.AgentToolExecutionStatus;
import com.ai.agent.tool.governance.AgentToolInvocationContext;
import com.ai.model.AgentProfile;
import com.ai.repository.AgentProfileRepository;
import com.ai.service.AgentExecutionTraceService;
import com.ai.conversation.ConversationAccessGuard;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 领取已批准 Run，恢复 Checkpoint，通过原工具管道执行并继续 ReAct。
 *
 * @author data-agent
 */
@Component
public class AgentResumeWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentResumeWorker.class);

    private final AgentDurableRuntimeProperties properties;
    private final AgentResumeStateService stateService;
    private final AgentRunLeaseService leaseService;
    private final AgentDurableRunStore runStore;
    private final AgentToolApprovalRepository approvalRepository;
    private final AgentCheckpointRestorer checkpointRestorer;
    private final AgentApprovalReauthorizationService reauthorizationService;
    private final AgentCapabilityService capabilityService;
    private final AgentProfileRepository agentProfileRepository;
    private final AgentToolInvoker toolInvoker;
    private final DurableToolContinuation continuation;
    private final AgentRunRegistry runRegistry;
    private final AgentApprovalTelemetry telemetry;
    private final AgentExecutionTraceService traceService;
    private final AgentOutcomeEvaluator outcomeEvaluator;
    private final AgentConversationRecorder conversationRecorder;
    private final AsyncTaskExecutor resumeExecutor;
    private final RagIndexMaintenance maintenance;
    private final ConversationAccessGuard conversationAccess;
    private final AgentApprovalResumeSignals resumeSignals;
    private final AgenticSupervisorService supervisor;
    private final BackgroundUserScope users;
    private final Set<String> scheduledApprovals = ConcurrentHashMap.newKeySet();

    public AgentResumeWorker(AgentDurableRuntimeProperties properties,
            AgentResumeStateService stateService,
            AgentRunLeaseService leaseService,
            AgentDurableRunStore runStore,
            AgentToolApprovalRepository approvalRepository,
            AgentCheckpointRestorer checkpointRestorer,
            AgentApprovalReauthorizationService reauthorizationService,
            AgentCapabilityService capabilityService,
            AgentProfileRepository agentProfileRepository,
            AgentToolInvoker toolInvoker,
            DurableToolContinuation continuation,
            AgentRunRegistry runRegistry,
            AgentApprovalTelemetry telemetry,
            AgentExecutionTraceService traceService,
            AgentOutcomeEvaluator outcomeEvaluator,
            AgentConversationRecorder conversationRecorder,
            @Qualifier("agentResumeExecutor") AsyncTaskExecutor resumeExecutor,
            RagIndexMaintenance maintenance, ConversationAccessGuard conversationAccess,
            AgentApprovalResumeSignals resumeSignals, AgenticSupervisorService supervisor, BackgroundUserScope users) {
        this.properties = properties;
        this.stateService = stateService;
        this.leaseService = leaseService;
        this.runStore = runStore;
        this.approvalRepository = approvalRepository;
        this.checkpointRestorer = checkpointRestorer;
        this.reauthorizationService = reauthorizationService;
        this.capabilityService = capabilityService;
        this.agentProfileRepository = agentProfileRepository;
        this.toolInvoker = toolInvoker;
        this.continuation = continuation;
        this.runRegistry = runRegistry;
        this.telemetry = telemetry;
        this.traceService = traceService;
        this.outcomeEvaluator = outcomeEvaluator;
        this.conversationRecorder = conversationRecorder;
        this.resumeExecutor = resumeExecutor;
        this.maintenance = maintenance;
        this.conversationAccess = conversationAccess;
        this.resumeSignals = resumeSignals;
        this.supervisor = supervisor;
        this.users = users;
    }

    @Scheduled(fixedDelayString = "${app.agent.durable.recovery-interval:PT1M}")
    public void resumeApprovedRuns() {
        if (!properties.isEnabled() || maintenance.isEnabled()) {
            return;
        }
        for (AgentToolApprovalEntity candidate : stateService.candidates()) {
            schedule(candidate.getApprovalId());
        }
    }

    /** 批准事务提交后立即唤醒；消息只负责提示，执行权仍由主库状态/CAS/租约决定。 */
    @RabbitListener(queues = BackgroundTaskConfiguration.RESUME_QUEUE, concurrency = "1")
    public void receiveResume(Message message) {
        if (message.getBody().length == 0 || message.getBody().length > 80) {
            throw new AmqpRejectAndDontRequeueException("无效审批恢复引用");
        }
        schedule(new String(message.getBody(), StandardCharsets.UTF_8));
    }

    private void schedule(String approvalId) {
        if (!properties.isEnabled() || maintenance.isEnabled() || !scheduledApprovals.add(approvalId)) return;
        try {
            resumeExecutor.execute(() -> {
                try {
                    var approval = approvalRepository.findById(approvalId).orElse(null);
                    if (approval != null && approval.getDecisionStatus() == AgentApprovalDecisionStatus.APPROVED
                            && (approval.getExecutionStatus() == AgentApprovalExecutionStatus.READY
                                || approval.getExecutionStatus() == AgentApprovalExecutionStatus.RUNNING)) {
                        resume(approvalId, approval.getRunId());
                    }
                } catch (Exception failure) {
                    LOGGER.warn("审批恢复任务等待主库补偿: approvalId={}, errorType={}",
                            approvalId, failure.getClass().getSimpleName());
                } finally {
                    scheduledApprovals.remove(approvalId);
                }
            });
        } catch (RuntimeException rejected) {
            scheduledApprovals.remove(approvalId);
            LOGGER.warn("审批恢复池已满，等待主库补偿: approvalId={}", approvalId);
        }
    }

    private void resume(String approvalId, String runId) {
        // 原请求或前一轮恢复尚未收尾时，不领取租约；收尾会补发信号，丢信号仍有分钟级补偿。
        if (runRegistry.findActive(runId).isPresent()) {
            return;
        }
        AgentRunLease lease = leaseService.claim(runId, approvalId).orElse(null);
        if (lease == null) {
            if (stateService.failExhaustedIfEligible(approvalId, runId)) {
                approvalRepository.findById(approvalId).ifPresent(approval -> {
                    recordResume(approval, AgentRunTerminationReason.RESUME_FAILED.name());
                    recordTrace(null, approval, AgentRunTerminationReason.RESUME_FAILED.name(),
                            "Agent Run 已达到最大恢复次数", null, null);
                });
            }
            return;
        }
        if (!stateService.startExecution(approvalId)) {
            leaseService.release(runId);
            return;
        }
        AgentRunContext context = null;
        AgentTaskContract checkpointTaskContract = null;
        try {
            AgentRunStateEntity run = runStore.find(runId).orElseThrow();
            AgentToolApprovalEntity approval = approvalRepository.findById(approvalId).orElseThrow();
            recordResume(approval, "STARTED");
            recordResumeProgress(approval, "STARTED", "已领取恢复租约并开始重新校验执行权限");
            AgentRunCheckpoint checkpoint;
            try {
                checkpoint = checkpointRestorer.decode(run);
                checkpointTaskContract = checkpoint.taskContract();
            } catch (Exception e) {
                failIfLeaseOwned(null, approvalId, runId, AgentRunTerminationReason.CHECKPOINT_INVALID,
                        "Checkpoint 无法解密或版本不受支持", null);
                return;
            }
            AgentApprovalReauthorization reauthorization;
            try {
                reauthorization = reauthorizationService.reauthorize(run, approval, checkpoint);
            } catch (SecurityException e) {
                failIfLeaseOwned(null, approvalId, runId, AgentRunTerminationReason.AUTHORIZATION_REVOKED,
                        "恢复前授权校验未通过", checkpointTaskContract);
                return;
            }
            RestoredAgentRun restored = checkpointRestorer.restore(
                    run, checkpoint, reauthorization.ownerAuthorization());
            context = restored.context();
            context.control().setExecutionFence(() -> {
                if (!leaseService.stillOwns(runId)) throw new IllegalStateException("审批恢复租约已失效");
            });
            // 即使删除发生在重新授权之后，也不能把恢复任务重新注册为可执行。
            conversationAccess.registerRun(context);
            ReActExecutionResult executionResult = executeRestored(restored, reauthorization);
            if (executionResult.suspended()) {
                stateService.completeSuspended(approvalId);
                recordResume(approval, "SUSPENDED_AGAIN");
                recordTrace(context, approval, "SUSPENDED_AGAIN", "等待新的工具动作审批", null,
                        checkpointTaskContract);
                return;
            }
            if (!leaseService.stillOwns(runId)) {
                LOGGER.warn("丢弃失租后的 Agent Run 结果: runId={}", runId);
                recordResume(approval, "LEASE_LOST");
                recordTrace(context, approval, "LEASE_LOST", "恢复租约已丢失，丢弃晚到结果", null,
                        checkpointTaskContract);
                return;
            }
            if (executionResult.success()) {
                conversationRecorder.recordResumedConversation(runId, executionResult.answer(),
                        checkpoint.supervisor() == null ? checkpoint.modelId() : checkpoint.supervisor().modelId());
                context.control().complete();
                stateService.completeTerminal(
                        approvalId,
                        context.snapshot(),
                        executionResult.answer(),
                        AgentApprovalExecutionStatus.SUCCEEDED);
                leaseService.release(runId);
                recordResume(approval, "SUCCEEDED");
                recordTrace(context, approval, "SUCCEEDED", "Agent Run 恢复完成",
                        executionResult.answer(), checkpointTaskContract);
            } else {
                failIfLeaseOwned(
                        context,
                        approvalId,
                        runId,
                        AgentRunTerminationReason.RESUME_FAILED,
                        "恢复后的模型调用失败",
                        checkpointTaskContract);
            }
        } catch (SecurityException e) {
            failIfLeaseOwned(context, approvalId, runId, AgentRunTerminationReason.AUTHORIZATION_REVOKED,
                    "恢复前 Agent 能力授权已变化", checkpointTaskContract);
        } catch (ToolResumeException e) {
            failIfLeaseOwned(context, approvalId, runId, AgentRunTerminationReason.TOOL_EXECUTION_FAILED,
                    "已批准工具执行失败", checkpointTaskContract);
        } catch (Exception e) {
            failIfLeaseOwned(context, approvalId, runId, AgentRunTerminationReason.RESUME_FAILED,
                    "Agent Run 恢复失败", checkpointTaskContract);
        } finally {
            if (context != null) {
                runRegistry.remove(context);
            }
            leaseService.release(runId);
            if (context != null && context.snapshot().status() == AgentRunStatus.WAITING_APPROVAL) {
                resumeSignals.wakeReady(runId);
            }
        }
    }

    private ReActExecutionResult executeRestored(RestoredAgentRun restored,
            AgentApprovalReauthorization reauthorization) {
        return users.call(restored.context().tenantId(), restored.context().userId(),
                () -> AgentRunScope.call(restored.context(), () -> {
                    AgentCapabilityBindingSnapshot capabilitySnapshot = restoredCapabilitySnapshot(restored);
                    if (capabilitySnapshot == null) {
                        return continueRestored(restored, reauthorization, restored.invocationContext());
                    }
                    if (!capabilitySnapshot.callableToolNames().contains(restored.checkpoint().pendingTool().toolName())) {
                        throw new SecurityException("待审批工具已不在原执行 Agent 的当前能力范围内");
                    }
                    AgentToolInvocationContext narrowedInvocation = narrowInvocationContext(
                            restored.invocationContext(), capabilitySnapshot);
                    return AgentCapabilityScope.call(capabilitySnapshot, () -> restored.checkpoint().supervisor() == null
                            ? continueRestored(restored, reauthorization, narrowedInvocation)
                            : supervisor.resume(restored.checkpoint().supervisor(),
                                () -> continueRestored(restored, reauthorization, narrowedInvocation)));
                }));
    }

    private ReActExecutionResult continueRestored(RestoredAgentRun restored,
            AgentApprovalReauthorization reauthorization,
            AgentToolInvocationContext invocationContext) {
        AgentPendingToolCheckpoint pending = restored.checkpoint().pendingTool();
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id(pending.providerRequestId())
                .name(pending.toolName())
                .arguments(pending.argumentsJson())
                .build();
        if (!leaseService.stillOwns(restored.context().runId())) {
            throw new IllegalStateException("执行工具前恢复租约已丢失");
        }
        AgentToolExecutionResult toolResult = toolInvoker.invokeApproved(
                request,
                invocationContext,
                reauthorization.grant());
        if (!toolResult.successful()) {
            throw new ToolResumeException();
        }
        restored.messages().add(ToolExecutionResultMessage.from(request, toolResult.toModelObservation()));
        return continuation.resume(
                restored.messages(),
                toolInvoker.buildExactToolSpecifications(invocationContext.allowedToolNames()),
                restored.checkpoint().modelId(),
                restored.context().sessionId(),
                restored.checkpoint().supervisor() == null
                        ? restored.context().executionContext().getRequest().getQuestion()
                        : restored.checkpoint().supervisor().pendingTask(),
                invocationContext);
    }

    private AgentCapabilityBindingSnapshot restoredCapabilitySnapshot(RestoredAgentRun restored) {
        String executorId = restored.checkpoint().executorId();
        if (executorId == null || !executorId.startsWith("profile:")) {
            return null;
        }
        String profileId = executorId.substring("profile:".length());
        AgentProfile profile = agentProfileRepository
                .findByAgentIdAndTenantId(profileId, restored.context().tenantId())
                .filter(AgentProfile::isEnabled)
                .orElseThrow(() -> new SecurityException("原执行 Agent 已删除、停用或不属于当前租户"));
        var current = capabilityService.resolveRuntimeBindings(profile, restored.checkpoint().mode());
        return restored.checkpoint().supervisor() == null ? current
                : AgenticSupervisorService.narrowCapabilities(restored.checkpoint().supervisor().leafCapabilities(), current);
    }

    private AgentToolInvocationContext narrowInvocationContext(
            AgentToolInvocationContext checkpointContext,
            AgentCapabilityBindingSnapshot capabilitySnapshot) {
        Set<String> currentlyCallable = capabilitySnapshot.callableToolNames();
        Set<String> allowedTools = checkpointContext.allowedToolNames().stream()
                .filter(currentlyCallable::contains)
                .collect(Collectors.toUnmodifiableSet());
        return new AgentToolInvocationContext(checkpointContext.executorId(), allowedTools,
                checkpointContext.initialToolNames().stream().filter(allowedTools::contains).collect(Collectors.toUnmodifiableSet()));
    }

    private void failIfLeaseOwned(AgentRunContext context,
            String approvalId,
            String runId,
            AgentRunTerminationReason reason,
            String detail,
            AgentTaskContract taskContract) {
        if (!leaseService.stillOwns(runId)) {
            if (context != null) {
                context.control().cancel("恢复租约已丢失");
            }
            approvalRepository.findById(approvalId).ifPresent(approval -> {
                recordResume(approval, "LEASE_LOST");
                recordTrace(context, approval, "LEASE_LOST", "恢复租约已丢失，当前节点停止执行",
                        null, taskContract);
            });
            return;
        }
        leaseService.release(runId);
        stateService.failClosed(approvalId, runId, reason, detail);
        approvalRepository.findById(approvalId).ifPresent(approval -> {
            recordResume(approval, reason.name());
            if (context != null) {
                context.control().fail(detail);
            }
            recordTrace(context, approval, reason.name(), detail, null, taskContract);
        });
    }

    private void recordResume(AgentToolApprovalEntity approval, String outcome) {
        try {
            telemetry.record(
                    "RESUME",
                    outcome,
                    approval.getTenantId(),
                    approval.getRequesterUserId(),
                    "agent-runtime",
                    approval.getApprovalId(),
                    approval.getRunId(),
                    approval.getToolCallId(),
                    approval.getToolName());
        } catch (RuntimeException e) {
            LOGGER.warn("Agent Run 恢复审计记录失败: runId={}, approvalId={}, outcome={}",
                    approval.getRunId(), approval.getApprovalId(), outcome);
        }
    }

    private void recordResumeProgress(AgentToolApprovalEntity approval, String outcome, String detail) {
        try {
            traceService.recordDurableResumeProgress(
                    approval.getRunId(),
                    approval.getTenantId(),
                    approval.getApprovalId(),
                    approval.getToolCallId(),
                    outcome,
                    detail);
        } catch (RuntimeException e) {
            LOGGER.warn("Agent Run 恢复进度 Trace 记录失败: runId={}, approvalId={}, outcome={}",
                    approval.getRunId(), approval.getApprovalId(), outcome);
        }
    }

    private void recordTrace(AgentRunContext context,
            AgentToolApprovalEntity approval,
            String outcome,
            String detail,
            String finalAnswer,
            AgentTaskContract taskContract) {
        try {
            AgentOutcomeEvaluation outcomeEvaluation = evaluateOutcome(
                    context, taskContract, approval, outcome, finalAnswer);
            if (context == null) {
                traceService.recordDurableResume(
                        approval.getRunId(),
                        approval.getTenantId(),
                        approval.getApprovalId(),
                        approval.getToolCallId(),
                        outcome,
                        detail,
                        taskContract,
                        outcomeEvaluation);
            } else {
                traceService.recordDurableResume(
                        context,
                        approval.getApprovalId(),
                        approval.getToolCallId(),
                        outcome,
                        detail,
                        outcomeEvaluation);
            }
        } catch (RuntimeException e) {
            LOGGER.warn("Agent Run 恢复 Trace 记录失败: runId={}, approvalId={}, outcome={}",
                    approval.getRunId(), approval.getApprovalId(), outcome);
        }
    }

    private AgentOutcomeEvaluation evaluateOutcome(AgentRunContext context,
            AgentTaskContract taskContract,
            AgentToolApprovalEntity approval,
            String resumeOutcome,
            String finalAnswer) {
        AgentTaskContract effectiveContract = context == null ? taskContract : context.taskContract();
        AgentRunStatus runStatus = context == null ? terminalStatus(resumeOutcome) : context.snapshot().status();
        if (runStatus == null) {
            return null;
        }
        Set<String> calledTools = context == null
                ? Set.of()
                : context.toolJournal().snapshot().stream()
                        .filter(record -> record.status() == AgentToolExecutionStatus.SUCCESS)
                        .map(record -> record.toolName())
                        .filter(name -> name != null && !name.isBlank())
                        .collect(Collectors.toUnmodifiableSet());
        Set<String> approvalStatuses = List.of(
                        approval.getDecisionStatus() == null ? "" : approval.getDecisionStatus().name(),
                        approval.getExecutionStatus() == null ? "" : approval.getExecutionStatus().name(),
                        resumeOutcome == null ? "" : resumeOutcome)
                .stream()
                .filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
        return outcomeEvaluator.evaluate(
                effectiveContract,
                new OutcomeEvidence(
                        finalAnswer,
                        runStatus,
                        calledTools,
                        approvalStatuses,
                        false,
                        false));
    }

    private AgentRunStatus terminalStatus(String resumeOutcome) {
        if ("SUCCEEDED".equals(resumeOutcome)) {
            return AgentRunStatus.COMPLETED;
        }
        if (AgentRunTerminationReason.CHECKPOINT_INVALID.name().equals(resumeOutcome)
                || AgentRunTerminationReason.AUTHORIZATION_REVOKED.name().equals(resumeOutcome)
                || AgentRunTerminationReason.TOOL_EXECUTION_FAILED.name().equals(resumeOutcome)
                || AgentRunTerminationReason.RESUME_FAILED.name().equals(resumeOutcome)) {
            return AgentRunStatus.FAILED;
        }
        return null;
    }

    private static final class ToolResumeException extends RuntimeException {
    }
}
