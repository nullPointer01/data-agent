package com.ai.agent.approval;

import com.ai.agent.durable.AgentCheckpointCodec;
import com.ai.agent.durable.AgentCheckpointMessageMapper;
import com.ai.agent.durable.AgentCheckpointVersion;
import com.ai.agent.durable.AgentDurableRunStore;
import com.ai.agent.durable.AgentDurableRuntimeProperties;
import com.ai.agent.durable.AgentPendingToolCheckpoint;
import com.ai.agent.durable.AgentRunBudgetCheckpoint;
import com.ai.agent.durable.AgentRunCheckpoint;
import com.ai.agent.durable.AgentRunControlFactory;
import com.ai.agent.runtime.AgentRunContext;
import com.ai.agent.runtime.AgentRunScope;
import com.ai.agent.runtime.AgentRunStatus;
import com.ai.agent.tool.governance.AgentToolApprovalContext;
import com.ai.agent.tool.governance.AgentToolExecutionResult;
import com.ai.agent.tool.governance.AgentToolInvocationContext;
import com.ai.util.CryptoUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ChatMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 在一个数据库事务中创建审批单并把 Run 推进到 WAITING_APPROVAL。
 *
 * @author data-agent
 */
@Service
public class AgentApprovalSuspensionService {

    private final AgentDurableRuntimeProperties properties;
    private final AgentRunControlFactory controlFactory;
    private final AgentCheckpointMessageMapper messageMapper;
    private final AgentCheckpointCodec checkpointCodec;
    private final AgentDurableRunStore runStore;
    private final AgentToolApprovalRepository approvalRepository;
    private final ObjectMapper objectMapper;
    private final CryptoUtil cryptoUtil;
    private final AgentApprovalTelemetry telemetry;

    public AgentApprovalSuspensionService(AgentDurableRuntimeProperties properties,
            AgentRunControlFactory controlFactory,
            AgentCheckpointMessageMapper messageMapper,
            AgentCheckpointCodec checkpointCodec,
            AgentDurableRunStore runStore,
            AgentToolApprovalRepository approvalRepository,
            ObjectMapper objectMapper,
            CryptoUtil cryptoUtil,
            AgentApprovalTelemetry telemetry) {
        this.properties = properties;
        this.controlFactory = controlFactory;
        this.messageMapper = messageMapper;
        this.checkpointCodec = checkpointCodec;
        this.runStore = runStore;
        this.approvalRepository = approvalRepository;
        this.objectMapper = objectMapper;
        this.cryptoUtil = cryptoUtil;
        this.telemetry = telemetry;
    }

    @Transactional
    public AgentApprovalSuspension suspend(ToolExecutionRequest request,
            AgentToolExecutionResult executionResult,
            AgentToolInvocationContext invocationContext,
            List<ChatMessage> messages) {
        AgentRunContext runContext = AgentRunScope.current()
                .orElseThrow(() -> new IllegalStateException("审批暂停缺少 Agent Run Context"));
        if (!properties.isEnabled() || !executionResult.approvalRequired()) {
            throw new IllegalStateException("持久化审批未启用或工具不需要审批");
        }
        if (!runContext.control().suspendForApproval("等待工具动作审批")) {
            throw new IllegalStateException("Agent Run 无法进入等待审批状态");
        }
        try {
            return persistSuspension(runContext, request, executionResult, invocationContext, messages);
        } catch (RuntimeException e) {
            runContext.control().failSuspension("审批现场持久化失败");
            throw e;
        }
    }

    private AgentApprovalSuspension persistSuspension(AgentRunContext runContext,
            ToolExecutionRequest request,
            AgentToolExecutionResult executionResult,
            AgentToolInvocationContext invocationContext,
            List<ChatMessage> messages) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.getApprovalTtl());
        String approvalId = UUID.randomUUID().toString();
        AgentToolApprovalContext approvalContext = executionResult.approvalContext();
        AgentPendingToolCheckpoint pendingTool = new AgentPendingToolCheckpoint(
                executionResult.toolCallId(),
                request.id(),
                executionResult.toolName(),
                normalizeArguments(request.arguments()),
                approvalContext.safeArgumentSummary(),
                now);
        AgentRunBudgetCheckpoint budget = controlFactory.freeze(runContext.control());
        AgentRunCheckpoint checkpoint = new AgentRunCheckpoint(
                AgentCheckpointVersion.CURRENT,
                runContext.mode(),
                "react",
                resolveModelId(runContext),
                invocationContext.executorId(),
                "TOOL_APPROVAL",
                budget.usedIterations(),
                0,
                "",
                messageMapper.captureForPending(messages, request),
                pendingTool,
                invocationContext.allowedToolNames(),
                budget,
                now);
        String checkpointCiphertext = checkpointCodec.encode(checkpoint);

        AgentToolApprovalEntity approval = new AgentToolApprovalEntity();
        approval.setApprovalId(approvalId);
        approval.setTenantId(runContext.tenantId());
        approval.setRunId(runContext.runId());
        approval.setToolCallId(executionResult.toolCallId());
        approval.setToolName(executionResult.toolName());
        approval.setRiskLevel(approvalContext.risk());
        approval.setRequesterUserId(runContext.userId());
        approval.setApprovalPermission(approvalContext.approvalPermission());
        approval.setSafeArgumentSummary(approvalContext.safeArgumentSummary());
        approval.setRequestCiphertext(encryptRequest(request, executionResult.toolCallId()));
        approval.setExpiresAt(expiresAt);
        approvalRepository.saveAndFlush(approval);

        boolean suspended = runStore.suspendForApproval(
                runContext.runId(),
                persistedActiveStatus(runContext.runId()),
                checkpoint,
                checkpointCiphertext,
                approvalId,
                expiresAt);
        if (!suspended) {
            throw new IllegalStateException("Agent Run 审批暂停发生版本冲突");
        }
        telemetry.record(
                "REQUEST",
                "PENDING",
                runContext.tenantId(),
                runContext.userId(),
                runContext.userId(),
                approvalId,
                runContext.runId(),
                executionResult.toolCallId(),
                executionResult.toolName());
        return new AgentApprovalSuspension(
                approvalId,
                runContext.runId(),
                executionResult.toolCallId(),
                executionResult.toolName(),
                approvalContext.safeArgumentSummary(),
                expiresAt);
    }

    private String encryptRequest(ToolExecutionRequest request, String toolCallId) {
        try {
            OriginalToolRequest original = new OriginalToolRequest(
                    toolCallId,
                    request.id(),
                    request.name(),
                    normalizeArguments(request.arguments()));
            String ciphertext = cryptoUtil.encrypt(objectMapper.writeValueAsString(original));
            if (ciphertext == null || !ciphertext.startsWith("ENC:")) {
                throw new IllegalStateException("审批工具请求未被加密");
            }
            return ciphertext;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("审批工具请求序列化失败", e);
        }
    }

    private String normalizeArguments(String arguments) {
        return arguments == null || arguments.isBlank() ? "{}" : arguments;
    }

    private String resolveModelId(AgentRunContext context) {
        String modelId = context.executionContext().getRequest().getModelId();
        return modelId == null ? "" : modelId;
    }

    private AgentRunStatus persistedActiveStatus(String runId) {
        return runStore.find(runId)
                .map(entity -> entity.getStatus() == AgentRunStatus.RESUMING
                        ? AgentRunStatus.RESUMING
                        : AgentRunStatus.RUNNING)
                .orElse(AgentRunStatus.RUNNING);
    }

    private record OriginalToolRequest(
            String toolCallId,
            String providerRequestId,
            String toolName,
            String argumentsJson) {
    }
}
