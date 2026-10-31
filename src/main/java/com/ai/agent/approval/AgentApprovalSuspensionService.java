package com.ai.agent.approval;

import com.ai.agent.agentic.SupervisorExecutionScope;
import com.ai.agent.agentic.SupervisorCheckpoint;
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
import com.ai.agent.tool.AgentConversationRecorder;
import com.ai.util.CryptoUtil;
import com.ai.conversation.ConversationAccessGuard;
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
    private final ConversationAccessGuard conversationAccess;
    private final AgentConversationRecorder conversations;

    public AgentApprovalSuspensionService(AgentDurableRuntimeProperties properties,
            AgentRunControlFactory controlFactory,
            AgentCheckpointMessageMapper messageMapper,
            AgentCheckpointCodec checkpointCodec,
            AgentDurableRunStore runStore,
            AgentToolApprovalRepository approvalRepository,
            ObjectMapper objectMapper,
            CryptoUtil cryptoUtil,
            AgentApprovalTelemetry telemetry, ConversationAccessGuard conversationAccess,
            AgentConversationRecorder conversations) {
        this.properties = properties;
        this.controlFactory = controlFactory;
        this.messageMapper = messageMapper;
        this.checkpointCodec = checkpointCodec;
        this.runStore = runStore;
        this.approvalRepository = approvalRepository;
        this.objectMapper = objectMapper;
        this.cryptoUtil = cryptoUtil;
        this.telemetry = telemetry;
        this.conversationAccess = conversationAccess;
        this.conversations = conversations;
    }

    @Transactional
    public AgentApprovalSuspension suspend(ToolExecutionRequest request,
            AgentToolExecutionResult executionResult,
            AgentToolInvocationContext invocationContext,
            List<ChatMessage> messages, String modelId) {
        SupervisorCheckpoint supervisor = SupervisorExecutionScope.capture(invocationContext.executorId());
        AgentRunContext runContext = AgentRunScope.current()
                .orElseThrow(() -> new IllegalStateException("审批暂停缺少 Agent Run Context"));
        conversationAccess.lockActive(runContext.sessionId(), runContext.tenantId(), runContext.userId());
        if (!properties.isEnabled() || !executionResult.approvalRequired()) {
            throw new IllegalStateException("持久化审批未启用或工具不需要审批");
        }
        if (!runContext.control().suspendForApproval("等待工具动作审批")) {
            throw new IllegalStateException("Agent Run 无法进入等待审批状态");
        }
        try {
            return persistSuspension(runContext, request, executionResult, invocationContext, messages, modelId, supervisor);
        } catch (RuntimeException e) {
            runContext.control().failSuspension("审批现场持久化失败");
            throw e;
        }
    }

    private AgentApprovalSuspension persistSuspension(AgentRunContext runContext,
            ToolExecutionRequest request,
            AgentToolExecutionResult executionResult,
            AgentToolInvocationContext invocationContext,
            List<ChatMessage> messages, String modelId, SupervisorCheckpoint supervisor) {
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
                supervisor == null ? runContext.mode() : supervisor.leafCapabilities().mode(),
                supervisor == null ? "react" : "supervisor",
                modelId,
                invocationContext.executorId(),
                "TOOL_APPROVAL",
                budget.usedIterations(),
                0,
                "",
                messageMapper.captureForPending(messages, request),
                pendingTool,
                invocationContext.allowedToolNames(),
                runContext.taskContract(),
                budget,
                now,
                supervisor,
                invocationContext.initialToolNames());
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
        approvalRepository.save(approval);

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
        // 审批可见前先保存可恢复的消息；批准速度不能决定是否能找到待更新的助手消息。
        if (runContext.executionContext().getOrigin().isConversationPersistenceEnabled()) {
            conversations.recordWaitingApprovalConversation(runContext.executionContext().getSession(),
                    runContext.executionContext().getRequest(), null, checkpoint.modelId(), runContext.runId());
        } else {
            conversations.updateRunConversation(runContext.runId(),
                    "工具操作已提交审批，审批通过后会继续执行。", null, checkpoint.modelId());
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
