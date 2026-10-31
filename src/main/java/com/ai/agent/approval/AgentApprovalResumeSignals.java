package com.ai.agent.approval;

import com.ai.agent.durable.AgentDurableRunStore;
import com.ai.agent.runtime.AgentRunStatus;
import com.ai.background.BackgroundTaskConfiguration;
import com.ai.background.TaskSignalPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 原执行收尾后再次检查已批准动作，避免首次 MQ 提示先于本机 Run 注册表释放。 */
@Service
public class AgentApprovalResumeSignals {
    private final AgentDurableRunStore runs;
    private final AgentToolApprovalRepository approvals;
    private final TaskSignalPublisher signals;

    public AgentApprovalResumeSignals(AgentDurableRunStore runs, AgentToolApprovalRepository approvals,
            TaskSignalPublisher signals) {
        this.runs = runs;
        this.approvals = approvals;
        this.signals = signals;
    }

    /** 仅登记当前已批准、未领取的审批，重复提示仍由执行侧状态和租约去重。 */
    @Transactional(readOnly = true)
    public void wakeReady(String runId) {
        if (!runs.isEnabled()) return;
        runs.find(runId).filter(run -> run.getStatus() == AgentRunStatus.WAITING_APPROVAL)
                .filter(run -> run.getApprovalId() != null)
                .flatMap(run -> approvals.findByApprovalIdAndTenantId(run.getApprovalId(), run.getTenantId()))
                .filter(approval -> approval.getDecisionStatus() == AgentApprovalDecisionStatus.APPROVED
                        && approval.getExecutionStatus() == AgentApprovalExecutionStatus.READY)
                .ifPresent(approval -> signals.afterCommit(BackgroundTaskConfiguration.RESUME_QUEUE, approval.getApprovalId()));
    }
}
