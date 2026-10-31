package com.ai.agent.durable;

import com.ai.agent.approval.AgentApprovalDecisionStatus;
import com.ai.agent.approval.AgentApprovalExecutionStatus;
import com.ai.agent.approval.AgentToolApprovalEntity;
import com.ai.agent.approval.AgentToolApprovalRepository;
import com.ai.agent.approval.ApprovalChanged;
import com.ai.agent.approval.ApprovalEventPublisher;
import com.ai.agent.runtime.AgentRunSnapshot;
import com.ai.agent.runtime.AgentRunStatus;
import com.ai.agent.runtime.AgentRunTerminationReason;
import com.ai.agent.tool.AgentConversationRecorder;
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
    private final AgentConversationRecorder conversations;
    private final ApprovalEventPublisher approvalEvents;

    public AgentResumeStateService(AgentToolApprovalRepository approvalRepository,
            AgentDurableRunStore runStore,
            AgentDurableRuntimeProperties properties, AgentConversationRecorder conversations,
            ApprovalEventPublisher approvalEvents) {
        this.approvalRepository = approvalRepository;
        this.runStore = runStore;
        this.properties = properties;
        this.conversations = conversations;
        this.approvalEvents = approvalEvents;
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
        int changed = approvalRepository.startExecution(
                approvalId,
                CLAIMABLE_EXECUTION,
                Instant.now(),
                approval.getVersion());
        if (changed == 1) notifyChange(approval);
        return changed == 1;
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
        // 与终态在同一事务保存；通知发出后不需要前端猜测等待 400ms 才能读到结果。
        conversations.updateRunConversation(runId, "操作未完成：" + safeDetail, null, null);
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
        conversations.updateRunConversation(runId, "审批恢复次数已耗尽，操作未完成。", null, null);
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
        notifyChange(approval);
    }

    private void notifyChange(AgentToolApprovalEntity approval) {
        // 再次挂起会改变 Run 的当前 approvalId；旧动作完成也要通知，不能只等新 Run 状态变化。
        runStore.find(approval.getRunId()).ifPresent(run -> approvalEvents.changed(new ApprovalChanged(
                run.getTenantId(), run.getUserId(), run.getSessionId(), run.getRunId(), approval.getApprovalId())));
    }

    private AgentToolApprovalEntity findApproval(String approvalId) {
        return approvalRepository.findById(approvalId)
                .orElseThrow(() -> new IllegalArgumentException("审批记录不存在"));
    }
}
