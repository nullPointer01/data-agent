package com.ai.agent.durable;

import com.ai.agent.approval.AgentApprovalDecisionStatus;
import com.ai.agent.approval.AgentApprovalExecutionStatus;
import com.ai.agent.approval.AgentToolApprovalEntity;
import com.ai.agent.approval.AgentToolApprovalRepository;
import com.ai.agent.runtime.AgentRunSnapshot;
import com.ai.agent.runtime.AgentRunStatus;
import com.ai.agent.runtime.AgentRunTerminationReason;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 恢复 worker 使用的短事务状态更新，不在事务中执行模型或工具。
 *
 * @author data-agent
 */
@Service
public class AgentResumeStateService {

    private static final List<AgentApprovalExecutionStatus> CLAIMABLE_EXECUTION = List.of(
            AgentApprovalExecutionStatus.READY,
            AgentApprovalExecutionStatus.RUNNING);

    private final AgentToolApprovalRepository approvalRepository;
    private final AgentDurableRunStore runStore;
    private final AgentDurableRuntimeProperties properties;

    public AgentResumeStateService(AgentToolApprovalRepository approvalRepository,
            AgentDurableRunStore runStore,
            AgentDurableRuntimeProperties properties) {
        this.approvalRepository = approvalRepository;
        this.runStore = runStore;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public List<AgentToolApprovalEntity> candidates() {
        int batchSize = properties.getScanBatchSize();
        List<AgentToolApprovalEntity> result = new ArrayList<>();
        result.addAll(approvalRepository.findByDecisionStatusAndExecutionStatusOrderByDecidedAtAsc(
                AgentApprovalDecisionStatus.APPROVED,
                AgentApprovalExecutionStatus.READY,
                PageRequest.of(0, batchSize)));
        if (result.size() < batchSize) {
            result.addAll(approvalRepository.findByDecisionStatusAndExecutionStatusOrderByDecidedAtAsc(
                    AgentApprovalDecisionStatus.APPROVED,
                    AgentApprovalExecutionStatus.RUNNING,
                    PageRequest.of(0, batchSize - result.size())));
        }
        return List.copyOf(result);
    }

    @Transactional
    public boolean startExecution(String approvalId) {
        AgentToolApprovalEntity approval = approvalRepository.findById(approvalId).orElse(null);
        if (approval == null || !CLAIMABLE_EXECUTION.contains(approval.getExecutionStatus())) {
            return false;
        }
        return approvalRepository.startExecution(
                approvalId,
                CLAIMABLE_EXECUTION,
                Instant.now(),
                approval.getVersion()) == 1;
    }

    @Transactional
    public void completeTerminal(String approvalId,
            AgentRunSnapshot snapshot,
            String resultSummary,
            AgentApprovalExecutionStatus executionStatus) {
        completeApproval(approvalId, executionStatus);
        if (!runStore.synchronizeTerminal(findApproval(approvalId).getRunId(), snapshot, resultSummary)) {
            throw new IllegalStateException("恢复结果与 Run 状态发生冲突");
        }
    }

    @Transactional
    public void completeSuspended(String approvalId) {
        completeApproval(approvalId, AgentApprovalExecutionStatus.SUCCEEDED);
    }

    @Transactional
    public void failClosed(String approvalId,
            String runId,
            AgentRunTerminationReason reason,
            String safeDetail) {
        AgentToolApprovalEntity approval = findApproval(approvalId);
        if (approval.getExecutionStatus() == AgentApprovalExecutionStatus.RUNNING) {
            completeApproval(approvalId, reason == AgentRunTerminationReason.AUTHORIZATION_REVOKED
                    ? AgentApprovalExecutionStatus.BLOCKED
                    : AgentApprovalExecutionStatus.FAILED);
        }
        if (!runStore.transition(
                runId,
                new AgentRunTransition(
                        AgentRunStatus.RESUMING,
                        AgentRunStatus.FAILED,
                        reason,
                        safeDetail))) {
            throw new IllegalStateException("恢复失败终态与 Run 状态发生冲突");
        }
    }

    @Transactional
    public boolean failExhaustedIfEligible(String approvalId, String runId) {
        AgentRunStateEntity run = runStore.find(runId).orElse(null);
        Instant now = Instant.now();
        if (run == null
                || (run.getStatus() != AgentRunStatus.RESUMING
                    && run.getStatus() != AgentRunStatus.WAITING_APPROVAL)
                || run.getResumeAttempts() < properties.getMaxResumeAttempts()
                || (run.getLeaseUntil() != null && run.getLeaseUntil().isAfter(now))) {
            return false;
        }
        AgentToolApprovalEntity approval = findApproval(approvalId);
        if (approval.getExecutionStatus() == AgentApprovalExecutionStatus.READY) {
            int started = approvalRepository.startExecution(
                    approvalId,
                    CLAIMABLE_EXECUTION,
                    now,
                    approval.getVersion());
            if (started != 1) {
                return false;
            }
        }
        completeApproval(approvalId, AgentApprovalExecutionStatus.FAILED);
        if (!runStore.transition(
                runId,
                new AgentRunTransition(
                        run.getStatus(),
                        AgentRunStatus.FAILED,
                        AgentRunTerminationReason.RESUME_FAILED,
                        "Agent Run 已达到最大恢复次数"))) {
            throw new IllegalStateException("恢复次数耗尽终态与 Run 状态发生冲突");
        }
        return true;
    }

    private void completeApproval(String approvalId, AgentApprovalExecutionStatus target) {
        AgentToolApprovalEntity approval = findApproval(approvalId);
        int changed = approvalRepository.completeExecution(
                approvalId,
                target,
                Instant.now(),
                approval.getVersion());
        if (changed != 1) {
            throw new IllegalStateException("审批执行状态发生版本冲突");
        }
    }

    private AgentToolApprovalEntity findApproval(String approvalId) {
        return approvalRepository.findById(approvalId)
                .orElseThrow(() -> new IllegalArgumentException("审批记录不存在"));
    }
}
