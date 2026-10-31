package com.ai.agent.durable;

import com.ai.agent.approval.AgentToolApprovalRepository;
import com.ai.agent.durable.dto.AgentDurableRunResponse;
import com.ai.agent.runtime.AgentRunStatus;
import com.ai.agent.runtime.AgentRunTerminationReason;
import com.ai.agent.tool.AgentConversationRecorder;
import com.ai.security.SecurityContextHelper;
import com.ai.conversation.ConversationAccessGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 面向当前认证所有者的持久化 Run 查询与取消服务。
 *
 * @author data-agent
 */
@Service
public class AgentDurableRunService {

    private static final String NOT_FOUND_OR_DENIED = "运行不存在或无权限";

    private final AgentDurableRunStore runStore;
    private final AgentToolApprovalRepository approvalRepository;
    private final SecurityContextHelper securityContextHelper;
    private final AgentConversationRecorder conversationRecorder;
    private final ConversationAccessGuard conversationAccess;

    public AgentDurableRunService(AgentDurableRunStore runStore,
            AgentToolApprovalRepository approvalRepository,
            SecurityContextHelper securityContextHelper,
            AgentConversationRecorder conversationRecorder, ConversationAccessGuard conversationAccess) {
        this.runStore = runStore;
        this.approvalRepository = approvalRepository;
        this.securityContextHelper = securityContextHelper;
        this.conversationRecorder = conversationRecorder;
        this.conversationAccess = conversationAccess;
    }

    @Transactional(readOnly = true)
    public AgentDurableRunResponse getOwned(String runId) {
        AgentRunStateEntity entity = findOwned(runId);
        return toResponse(entity);
    }

    @Transactional
    public AgentDurableRunResponse cancelOwned(String runId) {
        AgentRunStateEntity entity = findOwned(runId);
        if (entity.getStatus().isTerminal()) {
            return toResponse(entity);
        }
        conversationAccess.lockActive(entity.getSessionId(), entity.getTenantId(), entity.getUserId());
        entity = runStore.lock(runId);
        if (entity.getStatus().isTerminal()) return toResponse(entity);
        boolean cancelled = runStore.transition(
                runId,
                new AgentRunTransition(
                        entity.getStatus(),
                        AgentRunStatus.CANCELLED,
                        AgentRunTerminationReason.CANCELLED,
                        "用户取消运行"));
        if (cancelled) {
            approvalRepository.cancelUnfinishedByRun(entity.getTenantId(), runId, Instant.now());
            conversationRecorder.updateRunConversation(
                    entity.getRunId(), "任务已取消。已发出的模型请求或正在执行的操作可能仍会结束，请确认操作结果。", null, null);
        }
        return toResponse(findOwned(runId));
    }

    private AgentRunStateEntity findOwned(String runId) {
        if (!runStore.isEnabled()) {
            throw new IllegalArgumentException(NOT_FOUND_OR_DENIED);
        }
        AgentRunStateEntity run = runStore.findOwned(
                        runId,
                        securityContextHelper.getCurrentTenantId(),
                        securityContextHelper.getCurrentUserId())
                .orElseThrow(() -> new IllegalArgumentException(NOT_FOUND_OR_DENIED));
        if (run.getSessionId() != null && !run.getSessionId().isBlank()) {
            conversationAccess.requireReadable(run.getSessionId(), run.getTenantId(), run.getUserId());
        }
        return run;
    }

    private AgentDurableRunResponse toResponse(AgentRunStateEntity entity) {
        return new AgentDurableRunResponse(
                entity.getRunId(),
                entity.getSessionId(),
                entity.getAgentId(),
                entity.getMode().name(),
                entity.getStatus().name(),
                entity.getTerminationReason().name(),
                entity.getErrorSummary(),
                entity.getApprovalId(),
                entity.getApprovalExpiresAt(),
                entity.getUsedIterations(),
                entity.getUsedModelCalls(),
                entity.getUsedToolCalls(),
                entity.getUsedTokens(),
                entity.getRemainingActiveTimeoutMs(),
                entity.getResultSummary(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getCompletedAt());
    }
}
