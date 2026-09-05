package com.ai.agent.durable;

import com.ai.agent.approval.AgentToolApprovalRepository;
import com.ai.agent.durable.dto.AgentDurableRunResponse;
import com.ai.agent.runtime.AgentRunStatus;
import com.ai.agent.runtime.AgentRunTerminationReason;
import com.ai.security.SecurityContextHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 面向当前认证所有者的持久化 Run 查询与等待态取消服务。
 *
 * @author data-agent
 */
@Service
public class AgentDurableRunService {

    private static final String NOT_FOUND_OR_DENIED = "运行不存在或无权限";

    private final AgentDurableRunStore runStore;
    private final AgentToolApprovalRepository approvalRepository;
    private final SecurityContextHelper securityContextHelper;

    public AgentDurableRunService(AgentDurableRunStore runStore,
            AgentToolApprovalRepository approvalRepository,
            SecurityContextHelper securityContextHelper) {
        this.runStore = runStore;
        this.approvalRepository = approvalRepository;
        this.securityContextHelper = securityContextHelper;
    }

    @Transactional(readOnly = true)
    public AgentDurableRunResponse getOwned(String runId) {
        AgentRunStateEntity entity = findOwned(runId);
        return toResponse(entity);
    }

    @Transactional
    public AgentDurableRunResponse cancelWaitingOwned(String runId) {
        AgentRunStateEntity entity = findOwned(runId);
        if (entity.getStatus() != AgentRunStatus.WAITING_APPROVAL) {
            return toResponse(entity);
        }
        boolean cancelled = runStore.transition(
                runId,
                new AgentRunTransition(
                        AgentRunStatus.WAITING_APPROVAL,
                        AgentRunStatus.CANCELLED,
                        AgentRunTerminationReason.CANCELLED,
                        "用户取消等待审批的运行"));
        if (cancelled) {
            approvalRepository.cancelPendingByRun(entity.getTenantId(), runId, Instant.now());
        }
        return toResponse(findOwned(runId));
    }

    private AgentRunStateEntity findOwned(String runId) {
        if (!runStore.isEnabled()) {
            throw new IllegalArgumentException(NOT_FOUND_OR_DENIED);
        }
        return runStore.findOwned(
                        runId,
                        securityContextHelper.getCurrentTenantId(),
                        securityContextHelper.getCurrentUserId())
                .orElseThrow(() -> new IllegalArgumentException(NOT_FOUND_OR_DENIED));
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
