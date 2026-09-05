package com.ai.agent.durable;

import com.ai.agent.approval.AgentApprovalExecutionStatus;
import com.ai.agent.approval.AgentApprovalTelemetry;
import com.ai.agent.approval.AgentApprovalReauthorization;
import com.ai.agent.approval.AgentApprovalReauthorizationService;
import com.ai.agent.approval.AgentToolApprovalEntity;
import com.ai.agent.approval.AgentToolApprovalRepository;
import com.ai.agent.react.ReActExecutionResult;
import com.ai.agent.react.ReActLoopRunner;
import com.ai.agent.runtime.AgentRunContext;
import com.ai.agent.runtime.AgentRunRegistry;
import com.ai.agent.runtime.AgentRunScope;
import com.ai.agent.runtime.AgentRunTerminationReason;
import com.ai.agent.tool.AgentToolInvoker;
import com.ai.agent.tool.governance.AgentToolExecutionResult;
import com.ai.model.AnalysisResponse;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
    private final AgentToolInvoker toolInvoker;
    private final ReActLoopRunner loopRunner;
    private final AgentRunRegistry runRegistry;
    private final AgentApprovalTelemetry telemetry;
    private final AgentExecutionTraceService traceService;
    private final AsyncTaskExecutor resumeExecutor;
    private final Set<String> scheduledApprovals = ConcurrentHashMap.newKeySet();

    public AgentResumeWorker(AgentDurableRuntimeProperties properties,
            AgentResumeStateService stateService,
            AgentRunLeaseService leaseService,
            AgentDurableRunStore runStore,
            AgentToolApprovalRepository approvalRepository,
            AgentCheckpointRestorer checkpointRestorer,
            AgentApprovalReauthorizationService reauthorizationService,
            AgentToolInvoker toolInvoker,
            ReActLoopRunner loopRunner,
            AgentRunRegistry runRegistry,
            AgentApprovalTelemetry telemetry,
            AgentExecutionTraceService traceService,
            @Qualifier("agentResumeExecutor") AsyncTaskExecutor resumeExecutor) {
        this.properties = properties;
        this.stateService = stateService;
        this.leaseService = leaseService;
        this.runStore = runStore;
        this.approvalRepository = approvalRepository;
        this.checkpointRestorer = checkpointRestorer;
        this.reauthorizationService = reauthorizationService;
        this.toolInvoker = toolInvoker;
        this.loopRunner = loopRunner;
        this.runRegistry = runRegistry;
        this.telemetry = telemetry;
        this.traceService = traceService;
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
                            "Agent Run 已达到最大恢复次数");
                });
            }
            return;
        }
        if (!stateService.startExecution(approvalId)) {
            leaseService.release(runId);
            return;
        }
        AgentRunContext context = null;
        try {
            AgentRunStateEntity run = runStore.find(runId).orElseThrow();
            AgentToolApprovalEntity approval = approvalRepository.findById(approvalId).orElseThrow();
            recordResume(approval, "STARTED");
            AgentRunCheckpoint checkpoint;
            try {
                checkpoint = checkpointRestorer.decode(run);
            } catch (Exception e) {
                failIfLeaseOwned(null, approvalId, runId, AgentRunTerminationReason.CHECKPOINT_INVALID,
                        "Checkpoint 无法解密或版本不受支持");
                return;
            }
            AgentApprovalReauthorization reauthorization;
            try {
                reauthorization = reauthorizationService.reauthorize(run, approval, checkpoint);
            } catch (SecurityException e) {
                failIfLeaseOwned(null, approvalId, runId, AgentRunTerminationReason.AUTHORIZATION_REVOKED,
                        "恢复前授权校验未通过");
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
                recordTrace(context, approval, "SUSPENDED_AGAIN", "等待新的工具动作审批");
                return;
            }
            if (!leaseService.stillOwns(runId)) {
                LOGGER.warn("丢弃失租后的 Agent Run 结果: runId={}", runId);
                recordResume(approval, "LEASE_LOST");
                recordTrace(context, approval, "LEASE_LOST", "恢复租约已丢失，丢弃晚到结果");
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
                recordTrace(context, approval, "SUCCEEDED", "Agent Run 恢复完成");
            } else {
                failIfLeaseOwned(
                        context,
                        approvalId,
                        runId,
                        AgentRunTerminationReason.RESUME_FAILED,
                        "恢复后的模型调用失败");
            }
        } catch (ToolResumeException e) {
            failIfLeaseOwned(context, approvalId, runId, AgentRunTerminationReason.TOOL_EXECUTION_FAILED,
                    "已批准工具执行失败");
        } catch (Exception e) {
            failIfLeaseOwned(context, approvalId, runId, AgentRunTerminationReason.RESUME_FAILED,
                    "Agent Run 恢复失败");
        } finally {
            if (context != null) {
                runRegistry.remove(context);
            }
            leaseService.release(runId);
        }
    }

    private ReActExecutionResult executeRestored(RestoredAgentRun restored,
            AgentApprovalReauthorization reauthorization) {
        AgentPendingToolCheckpoint pending = restored.checkpoint().pendingTool();
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id(pending.providerRequestId())
                .name(pending.toolName())
                .arguments(pending.argumentsJson())
                .build();
        return AgentRunScope.call(restored.context(), () -> {
            if (!leaseService.stillOwns(restored.context().runId())) {
                throw new IllegalStateException("执行工具前恢复租约已丢失");
            }
            AgentToolExecutionResult toolResult = toolInvoker.invokeApproved(
                    request,
                    restored.invocationContext(),
                    reauthorization.grant());
            if (!toolResult.successful()) {
                throw new ToolResumeException();
            }
            restored.messages().add(ToolExecutionResultMessage.from(request, toolResult.toModelObservation()));
            return loopRunner.run(
                    restored.messages(),
                    toolInvoker.buildToolSpecifications(new ArrayList<>(
                            restored.invocationContext().allowedToolNames())),
                    restored.checkpoint().modelId(),
                    restored.context().sessionId(),
                    restored.context().executionContext().getRequest().getQuestion(),
                    new ArrayList<AnalysisResponse.ThinkingStep>(),
                    restored.invocationContext());
        });
    }

    private void failIfLeaseOwned(AgentRunContext context,
            String approvalId,
            String runId,
            AgentRunTerminationReason reason,
            String detail) {
        if (!leaseService.stillOwns(runId)) {
            if (context != null) {
                context.control().cancel("恢复租约已丢失");
            }
            approvalRepository.findById(approvalId).ifPresent(approval -> {
                recordResume(approval, "LEASE_LOST");
                recordTrace(context, approval, "LEASE_LOST", "恢复租约已丢失，当前节点停止执行");
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
            recordTrace(context, approval, reason.name(), detail);
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

    private void recordTrace(AgentRunContext context,
            AgentToolApprovalEntity approval,
            String outcome,
            String detail) {
        try {
            if (context == null) {
                traceService.recordDurableResume(
                        approval.getRunId(),
                        approval.getTenantId(),
                        approval.getApprovalId(),
                        approval.getToolCallId(),
                        outcome,
                        detail);
            } else {
                traceService.recordDurableResume(
                        context,
                        approval.getApprovalId(),
                        approval.getToolCallId(),
                        outcome,
                        detail);
            }
        } catch (RuntimeException e) {
            LOGGER.warn("Agent Run 恢复 Trace 记录失败: runId={}, approvalId={}, outcome={}",
                    approval.getRunId(), approval.getApprovalId(), outcome);
        }
    }

    private static final class ToolResumeException extends RuntimeException {
    }
}
