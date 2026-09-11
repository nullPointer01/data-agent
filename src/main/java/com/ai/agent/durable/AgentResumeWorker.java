package com.ai.agent.durable;

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
import com.ai.agent.react.ReActLoopRunner;
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
import com.ai.model.AnalysisResponse;
import com.ai.repository.AgentProfileRepository;
import com.ai.service.AgentExecutionTraceService;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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
    private final ReActLoopRunner loopRunner;
    private final AgentRunRegistry runRegistry;
    private final AgentApprovalTelemetry telemetry;
    private final AgentExecutionTraceService traceService;
    private final AgentOutcomeEvaluator outcomeEvaluator;
    private final AgentConversationRecorder conversationRecorder;
    private final AsyncTaskExecutor resumeExecutor;
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
            ReActLoopRunner loopRunner,
            AgentRunRegistry runRegistry,
            AgentApprovalTelemetry telemetry,
            AgentExecutionTraceService traceService,
            AgentOutcomeEvaluator outcomeEvaluator,
            AgentConversationRecorder conversationRecorder,
            @Qualifier("agentResumeExecutor") AsyncTaskExecutor resumeExecutor) {
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
        this.loopRunner = loopRunner;
        this.runRegistry = runRegistry;
        this.telemetry = telemetry;
        this.traceService = traceService;
        this.outcomeEvaluator = outcomeEvaluator;
        this.conversationRecorder = conversationRecorder;
        this.resumeExecutor = resumeExecutor;
    }

    @Scheduled(fixedDelayString = "${app.agent.durable.scan-interval:PT15S}")
    public void resumeApprovedRuns() {
        if (!properties.isEnabled()) {
            return;
        }
        for (AgentToolApprovalEntity candidate : stateService.candidates()) {
            if (!scheduledApprovals.add(candidate.getApprovalId())) {
                continue;
            }
            try {
                resumeExecutor.execute(() -> {
                    try {
                        resume(candidate.getApprovalId(), candidate.getRunId());
                    } catch (Exception e) {
                        LOGGER.warn("Agent Run 恢复扫描项失败: runId={}, approvalId={}, error={}",
                                candidate.getRunId(), candidate.getApprovalId(), e.getMessage());
                    } finally {
                        scheduledApprovals.remove(candidate.getApprovalId());
                    }
                });
            } catch (RuntimeException e) {
                scheduledApprovals.remove(candidate.getApprovalId());
                LOGGER.warn("Agent Run 恢复任务提交失败: runId={}, approvalId={}, error={}",
                        candidate.getRunId(), candidate.getApprovalId(), e.getMessage());
            }
        }
    }

    private void resume(String approvalId, String runId) {
        AgentRunLease lease = leaseService.claim(runId, approvalId).orElse(null);
        if (lease == null) {
            if (stateService.failExhaustedIfEligible(approvalId, runId)) {
                approvalRepository.findById(approvalId).ifPresent(approval -> {
                    recordResume(approval, AgentRunTerminationReason.RESUME_FAILED.name());
                    recordTrace(null, approval, AgentRunTerminationReason.RESUME_FAILED.name(),
                            "Agent Run 已达到最大恢复次数", null, null);
                    updateConversation(runId, "审批恢复次数已耗尽，操作未完成。", null, null);
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
            runRegistry.register(context);
            ReActExecutionResult executionResult = executeRestored(restored, reauthorization);
            if (executionResult.suspended()) {
                stateService.completeSuspended(approvalId);
                recordResume(approval, "SUSPENDED_AGAIN");
                recordTrace(context, approval, "SUSPENDED_AGAIN", "等待新的工具动作审批", null,
                        checkpointTaskContract);
                updateConversation(runId, "工具操作已再次提交审批，审批通过后会继续执行。",
                        null, checkpoint.modelId());
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
                context.control().complete();
                leaseService.release(runId);
                stateService.completeTerminal(
                        approvalId,
                        context.snapshot(),
                        executionResult.answer(),
                        AgentApprovalExecutionStatus.SUCCEEDED);
                recordResume(approval, "SUCCEEDED");
                recordTrace(context, approval, "SUCCEEDED", "Agent Run 恢复完成",
                        executionResult.answer(), checkpointTaskContract);
                updateConversation(runId, executionResult.answer(), null, checkpoint.modelId());
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
        }
    }

    private ReActExecutionResult executeRestored(RestoredAgentRun restored,
            AgentApprovalReauthorization reauthorization) {
        return AgentRunScope.call(restored.context(), () -> {
            AgentCapabilityBindingSnapshot capabilitySnapshot = restoredCapabilitySnapshot(restored);
            if (capabilitySnapshot == null) {
                return continueRestored(restored, reauthorization, restored.invocationContext());
            }
            if (!capabilitySnapshot.allowsTool(restored.checkpoint().pendingTool().toolName())) {
                throw new SecurityException("待审批工具已不在原执行 Agent 的当前能力范围内");
            }
            AgentToolInvocationContext narrowedInvocation = narrowInvocationContext(
                    restored.invocationContext(), capabilitySnapshot);
            return AgentCapabilityScope.call(capabilitySnapshot,
                    () -> continueRestored(restored, reauthorization, narrowedInvocation));
        });
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
        return loopRunner.run(
                restored.messages(),
                toolInvoker.buildToolSpecifications().stream()
                        .filter(specification -> invocationContext.allowedToolNames().contains(specification.name()))
                        .toList(),
                restored.checkpoint().modelId(),
                restored.context().sessionId(),
                restored.context().executionContext().getRequest().getQuestion(),
                new ArrayList<AnalysisResponse.ThinkingStep>(),
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
        return capabilityService.resolveRuntimeBindings(profile, restored.checkpoint().mode());
    }

    private AgentToolInvocationContext narrowInvocationContext(
            AgentToolInvocationContext checkpointContext,
            AgentCapabilityBindingSnapshot capabilitySnapshot) {
        Set<String> allowedTools = checkpointContext.allowedToolNames().stream()
                .filter(capabilitySnapshot.toolNames()::contains)
                .collect(Collectors.toUnmodifiableSet());
        return new AgentToolInvocationContext(checkpointContext.executorId(), allowedTools);
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
        updateConversation(runId, "操作未完成：" + detail, null, null);
        approvalRepository.findById(approvalId).ifPresent(approval -> {
            recordResume(approval, reason.name());
            if (context != null) {
                context.control().fail(detail);
            }
            recordTrace(context, approval, reason.name(), detail, null, taskContract);
        });
    }

    private void updateConversation(String runId, String content, String skillUsed, String modelId) {
        conversationRecorder.updateRunConversation(runId, content, skillUsed, modelId);
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
