package com.ai.agent.tool.governance;

import com.ai.agent.approval.AgentApprovalGrant;
import com.ai.agent.runtime.AgentRunContext;
import com.ai.agent.runtime.AgentRunScope;
import com.ai.agent.runtime.AgentRunTerminatedException;
import com.ai.agent.runtime.event.AgentEvent;
import com.ai.agent.runtime.event.AgentEventType;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Agent 工具唯一执行管道，固定完成校验、授权、预算、执行和安全观测。
 *
 * @author data-agent
 */
@Component
public class AgentToolExecutionPipeline {

    private final AgentToolAdmissionService admissionService;
    private final AgentToolRetryPolicy retryPolicy;
    private final AgentToolFailureClassifier failureClassifier;
    private final AgentToolOutputSanitizer outputSanitizer;
    private final AgentToolTelemetry telemetry;
    private final AgentToolGovernanceProperties properties;
    private final AsyncTaskExecutor executor;

    public AgentToolExecutionPipeline(AgentToolAdmissionService admissionService,
            AgentToolRetryPolicy retryPolicy,
            AgentToolFailureClassifier failureClassifier,
            AgentToolOutputSanitizer outputSanitizer,
            AgentToolTelemetry telemetry,
            AgentToolGovernanceProperties properties,
            @Qualifier("agentToolExecutor") AsyncTaskExecutor executor) {
        this.admissionService = admissionService;
        this.retryPolicy = retryPolicy;
        this.failureClassifier = failureClassifier;
        this.outputSanitizer = outputSanitizer;
        this.telemetry = telemetry;
        this.properties = properties;
        this.executor = executor;
    }

    public AgentToolExecutionResult execute(ToolExecutionRequest request,
            AgentToolInvocationContext invocationContext) {
        long startedNanos = System.nanoTime();
        AgentToolAdmission admission = admissionService.admit(request, invocationContext);
        return executeAdmission(admission, invocationContext, startedNanos);
    }

    /**
     * 只执行工具准入，不消费预算也不调用工具实现。
     *
     * @param request 模型工具请求
     * @param invocationContext 当前执行者白名单
     * @return 可供同一调用链后续执行的准入结果
     */
    public AgentToolAdmission admit(ToolExecutionRequest request,
            AgentToolInvocationContext invocationContext) {
        return admissionService.admit(request, invocationContext);
    }

    /**
     * 执行已经完成准入的工具请求，避免并行工具预检后重复解析安全策略。
     *
     * @param admission 预检生成的准入结果
     * @param invocationContext 当前执行者白名单
     * @return 结构化安全结果
     */
    public AgentToolExecutionResult execute(AgentToolAdmission admission,
            AgentToolInvocationContext invocationContext) {
        return executeAdmission(admission, invocationContext, System.nanoTime());
    }

    public AgentToolExecutionResult executeApproved(ToolExecutionRequest request,
            AgentToolInvocationContext invocationContext,
            AgentApprovalGrant grant) {
        long startedNanos = System.nanoTime();
        AgentToolAdmission admission = admissionService.admitApproved(request, invocationContext, grant);
        return executeAdmission(admission, invocationContext, startedNanos);
    }

    private AgentToolExecutionResult executeAdmission(AgentToolAdmission admission,
            AgentToolInvocationContext invocationContext,
            long startedNanos) {
        AgentToolAdmission.AdmittedToolCall admitted = admission.admittedToolCall();
        if (!admission.admitted()) {
            AgentRunContext runContext = admitted == null
                    ? AgentRunScope.current().orElse(null)
                    : admitted.runContext();
            AgentToolDescriptor descriptor = admitted == null ? null : admitted.descriptor();
            String toolCallId = admitted == null ? "" : admitted.toolCallId();
            String toolName = descriptor == null ? "unknown" : descriptor.name();
            String argumentSummary = admitted == null ? "{}" : admitted.validation().safeSummary();
            AgentToolExecutionResult result = finish(runContext, invocationContext, descriptor, toolCallId, toolName,
                    admission.status(), false, 0, startedNanos, argumentSummary,
                    admission.safeMessage(), admission.approvalRequired());
            if (admission.approvalRequired()) {
                AgentToolApprovalContext approvalContext = new AgentToolApprovalContext(
                        descriptor.risk(),
                        descriptor.approvalPermission(),
                        argumentSummary);
                return new AgentToolExecutionResult(
                        result.toolCallId(),
                        result.toolName(),
                        result.status(),
                        result.retriable(),
                        result.attempts(),
                        result.durationMs(),
                        result.payload(),
                        approvalContext);
            }
            return result;
        }
        return executeAdmitted(admitted, startedNanos);
    }

    private AgentToolExecutionResult executeAdmitted(
            AgentToolAdmission.AdmittedToolCall admitted,
            long startedNanos) {
        AgentRunContext runContext = admitted.runContext();
        AgentToolInvocationContext invocationContext = admitted.invocationContext();
        AgentToolRegistry.RegisteredTool registered = admitted.registeredTool();
        AgentToolDescriptor descriptor = admitted.descriptor();
        String toolCallId = admitted.toolCallId();
        AgentToolArgumentValidation validation = admitted.validation();
        ToolExecutionRequest request = admitted.request();

        int attempts = 0;
        AgentToolFailure lastFailure = null;
        while (attempts < retryPolicy.maximumAttempts(descriptor)) {
            try {
                runContext.control().ensureActive();
                runContext.control().beforeToolCall();
            } catch (AgentRunTerminatedException e) {
                lastFailure = failureClassifier.classify(e);
                break;
            }
            attempts++;
            Future<String> future = null;
            try {
                Duration timeout = effectiveTimeout(runContext, descriptor);
                future = executor.submit(() -> registered.executor().execute(request, null));
                String rawResult = future.get(Math.max(1L, timeout.toMillis()), TimeUnit.MILLISECONDS);
                recordAttempt(runContext, descriptor, "success");
                return finish(runContext, invocationContext, descriptor, toolCallId, descriptor.name(),
                        AgentToolExecutionStatus.SUCCESS, false, attempts, startedNanos,
                        validation.safeSummary(), rawResult, true);
            } catch (TimeoutException e) {
                if (future != null) {
                    future.cancel(true);
                }
                recordAttempt(runContext, descriptor, "timeout");
                lastFailure = new AgentToolFailure(AgentToolExecutionStatus.TIMEOUT, true, "工具调用超时");
            } catch (InterruptedException e) {
                if (future != null) {
                    future.cancel(true);
                }
                Thread.currentThread().interrupt();
                recordAttempt(runContext, descriptor, "run_terminated");
                lastFailure = new AgentToolFailure(AgentToolExecutionStatus.RUN_TERMINATED, false,
                        "Agent Run 已终止");
            } catch (Exception e) {
                if (future != null && !future.isDone()) {
                    future.cancel(true);
                }
                lastFailure = failureClassifier.classify(e);
                recordAttempt(runContext, descriptor, lastFailure.status().name().toLowerCase());
            }
            if (!retryPolicy.canRetry(descriptor, lastFailure, attempts, runContext)
                    || !awaitBackoff(runContext, retryPolicy.backoffAfter(attempts))) {
                break;
            }
        }
        AgentToolFailure failure = lastFailure == null
                ? new AgentToolFailure(AgentToolExecutionStatus.EXECUTION_FAILED, false, "工具执行失败")
                : lastFailure;
        return finish(runContext, invocationContext, descriptor, toolCallId, descriptor.name(),
                failure.status(), failure.retriable(), attempts, startedNanos,
                validation.safeSummary(), failure.safeMessage(), true);
    }

    private Duration effectiveTimeout(AgentRunContext context, AgentToolDescriptor descriptor) {
        Duration configured = min(descriptor.timeout(), properties.getDefaultTimeout());
        Duration remaining = context.control().remainingTime();
        if (remaining.isZero() || remaining.isNegative()) {
            context.control().ensureActive();
        }
        return min(configured, remaining);
    }

    private boolean awaitBackoff(AgentRunContext context, Duration requested) {
        Duration wait = min(requested, context.control().remainingTime());
        if (wait.isZero() || wait.isNegative()) {
            return false;
        }
        try {
            Thread.sleep(wait.toMillis());
            context.control().ensureActive();
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (AgentRunTerminatedException e) {
            return false;
        }
    }

    private AgentToolExecutionResult finish(AgentRunContext runContext,
            AgentToolInvocationContext invocationContext,
            AgentToolDescriptor descriptor,
            String toolCallId,
            String toolName,
            AgentToolExecutionStatus status,
            boolean retriable,
            int attempts,
            long startedNanos,
            String argumentSummary,
            String rawPayload,
            boolean authorized) {
        AgentToolSafePayload safePayload = outputSanitizer.sanitize(rawPayload, descriptor);
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
        AgentToolExecutionResult result = new AgentToolExecutionResult(toolCallId, toolName, status,
                retriable, attempts, durationMs, safePayload);
        telemetry.complete(runContext, invocationContext, descriptor, result, argumentSummary, authorized);
        return result;
    }

    private void recordAttempt(AgentRunContext context, AgentToolDescriptor descriptor, String outcome) {
        telemetry.recordAttempt(descriptor, outcome);
        if (context != null && context.eventSink().isStreaming()) {
            context.eventSink().emit(AgentEvent.of(context, AgentEventType.BUDGET_UPDATED,
                    Map.of("usage", context.snapshot())));
        }
    }

    private Duration min(Duration first, Duration second) {
        return first.compareTo(second) <= 0 ? first : second;
    }
}
