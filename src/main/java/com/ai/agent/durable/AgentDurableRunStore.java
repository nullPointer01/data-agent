package com.ai.agent.durable;

import com.ai.agent.runtime.AgentRunContext;
import com.ai.agent.runtime.AgentRunLimits;
import com.ai.agent.runtime.AgentRunSnapshot;
import com.ai.agent.runtime.AgentRunStatus;
import com.ai.agent.runtime.AgentRunTerminationReason;
import com.ai.conversation.ConversationAccessGuard;
import com.ai.agent.approval.ApprovalEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Run 状态唯一写边界。除初次创建外，所有写入都使用状态与版本双重 CAS。
 *
 * @author data-agent
 */
@Service
public class AgentDurableRunStore {

    private final AgentRunStateRepository repository;
    private final AgentDurableRuntimeProperties properties;
    private final ConversationAccessGuard conversationAccess;
    private final ApprovalEventPublisher approvalEvents;

    public AgentDurableRunStore(AgentRunStateRepository repository,
            AgentDurableRuntimeProperties properties, ConversationAccessGuard conversationAccess,
            ApprovalEventPublisher approvalEvents) {
        this.repository = repository;
        this.properties = properties;
        this.conversationAccess = conversationAccess;
        this.approvalEvents = approvalEvents;
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    @Transactional
    public boolean bindMode(String runId, com.ai.agent.runtime.AgentExecutionMode mode) {
        return repository.bindMode(runId, mode) == 1;
    }

    @Transactional
    public AgentRunStateEntity create(AgentRunContext context) {
        if (!isEnabled()) {
            throw new IllegalStateException("持久化 Agent Runtime 未启用");
        }
        conversationAccess.lockActive(context.sessionId(), context.tenantId(), context.userId());
        AgentRunLimits limits = context.control().getLimits();
        AgentRunStateEntity entity = new AgentRunStateEntity();
        entity.setRunId(context.runId());
        entity.setTenantId(requireText(context.tenantId(), "tenantId"));
        entity.setUserId(requireText(context.userId(), "userId"));
        entity.setSessionId(context.sessionId());
        entity.setAgentId(context.agentId());
        entity.setMode(context.mode());
        entity.setStatus(AgentRunStatus.CREATED);
        entity.setTerminationReason(AgentRunTerminationReason.NONE);
        entity.setTimeoutMs(limits.timeout().toMillis());
        entity.setMaxIterations(limits.maxIterations());
        entity.setMaxModelCalls(limits.maxModelCalls());
        entity.setMaxToolCalls(limits.maxToolCalls());
        entity.setMaxTokens(limits.maxTokens());
        entity.setRemainingActiveTimeoutMs(limits.timeout().toMillis());
        return repository.save(entity);
    }

    @Transactional
    public boolean transition(String runId, AgentRunTransition transition) {
        AgentRunStateEntity current = findExpected(runId, transition.expectedStatus()).orElse(null);
        if (current == null) {
            return false;
        }
        Instant now = Instant.now();
        int changed = repository.transition(
                runId,
                transition.expectedStatus(),
                transition.targetStatus(),
                transition.reason(),
                truncate(transition.detail(), 1024),
                transition.targetStatus().isTerminal() ? now : null,
                now,
                current.getVersion());
        if (changed == 1) approvalEvents.changed(current);
        return changed == 1;
    }

    @Transactional
    public boolean terminate(String runId, AgentRunStatus expectedStatus, AgentRunSnapshot snapshot,
            String resultSummary) {
        if (snapshot == null || !snapshot.status().isTerminal()) {
            throw new IllegalArgumentException("持久化终态必须提供终态快照");
        }
        AgentRunTransition transition = new AgentRunTransition(
                expectedStatus,
                snapshot.status(),
                snapshot.terminationReason(),
                snapshot.detail());
        AgentRunStateEntity current = findExpected(runId, expectedStatus).orElse(null);
        if (current == null) {
            return false;
        }
        Instant now = Instant.now();
        int changed = repository.terminate(
                runId,
                transition.expectedStatus(),
                transition.targetStatus(),
                transition.reason(),
                truncate(transition.detail(), 1024),
                truncate(resultSummary, 20_000),
                snapshot.iterations(),
                snapshot.modelCalls(),
                snapshot.toolCalls(),
                snapshot.tokens(),
                snapshot.tokenUsageEstimated(),
                snapshot.remainingActiveTimeoutMs(),
                now,
                now,
                current.getVersion());
        if (changed == 1) approvalEvents.changed(current);
        return changed == 1;
    }

    @Transactional
    public boolean synchronizeTerminal(String runId, AgentRunSnapshot snapshot, String resultSummary) {
        if (snapshot == null || !snapshot.status().isTerminal()) {
            return false;
        }
        AgentRunStateEntity current = repository.findById(runId).orElse(null);
        if (current == null || current.getStatus().isTerminal()) {
            return false;
        }
        return terminate(runId, current.getStatus(), snapshot, resultSummary);
    }

    @Transactional
    public boolean suspendForApproval(String runId,
            AgentRunStatus expectedStatus,
            AgentRunCheckpoint checkpoint,
            String checkpointCiphertext,
            String approvalId,
            Instant approvalExpiresAt) {
        AgentRunStateEntity current = findExpected(runId, expectedStatus).orElse(null);
        if (current == null) {
            return false;
        }
        AgentRunBudgetCheckpoint budget = checkpoint.budget();
        com.ai.agent.runtime.AgentRunScope.current().ifPresent(context -> context.control().verifyExecutionFence());
        int changed = repository.suspendForApproval(
                runId,
                expectedStatus,
                requireEncrypted(checkpointCiphertext),
                checkpoint.schemaVersion(),
                budget.usedIterations(),
                budget.usedModelCalls(),
                budget.usedToolCalls(),
                budget.usedTokens(),
                budget.tokenUsageEstimated(),
                budget.remainingActiveTimeoutMs(),
                requireText(approvalId, "approvalId"),
                approvalExpiresAt,
                Instant.now(),
                current.getVersion());
        if (changed == 1) {
            current.setApprovalId(approvalId);
            approvalEvents.changed(current);
        }
        return changed == 1;
    }

    @Transactional(readOnly = true)
    public Optional<AgentRunStateEntity> findOwned(String runId, String tenantId, String userId) {
        return repository.findByRunIdAndTenantIdAndUserId(runId, tenantId, userId);
    }

    @Transactional(readOnly = true)
    public Optional<AgentRunStateEntity> find(String runId) {
        return repository.findById(runId);
    }

    /** 调用方先持有会话锁，再锁 Run，取消与正常完成使用同一个顺序。 */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public AgentRunStateEntity lock(String runId) { return repository.findForUpdate(runId).orElseThrow(); }

    private Optional<AgentRunStateEntity> findExpected(String runId, AgentRunStatus expectedStatus) {
        return repository.findById(runId).filter(entity -> entity.getStatus() == expectedStatus);
    }

    private String requireEncrypted(String ciphertext) {
        if (ciphertext == null || !ciphertext.startsWith("ENC:")) {
            throw new IllegalArgumentException("Checkpoint 必须是加密载荷");
        }
        return ciphertext;
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Agent Run " + field + " 不能为空");
        }
        return value;
    }

    private String truncate(String value, int maximum) {
        if (value == null || value.length() <= maximum) {
            return value;
        }
        return value.substring(0, maximum);
    }
}
