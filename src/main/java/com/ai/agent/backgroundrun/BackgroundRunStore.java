package com.ai.agent.backgroundrun;

import com.ai.agent.AgentRequestPreparationService;
import com.ai.agent.approval.ApprovalChanged;
import com.ai.agent.approval.ApprovalEventPublisher;
import com.ai.agent.durable.AgentDurableRunStore;
import com.ai.agent.durable.AgentRunStateEntity;
import com.ai.agent.durable.AgentRunTransition;
import com.ai.agent.runtime.AgentRunCoordinator;
import com.ai.agent.runtime.AgentRunStatus;
import com.ai.agent.runtime.AgentRunTerminationReason;
import com.ai.background.BackgroundTaskConfiguration;
import com.ai.background.TaskSignalPublisher;
import com.ai.conversation.ConversationAccessGuard;
import com.ai.conversation.ConversationLifecycleMapper;
import com.ai.memory.MemoryCaptureMode;
import com.ai.model.AnalysisRequest;
import com.ai.security.SecurityContextHelper;
import com.ai.service.SessionManager;
import com.ai.util.CryptoUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;

/** 提交、首次领取、事件写入和失租收敛的事务边界；网络模型调用始终在事务外。 */
@Service
public class BackgroundRunStore {
    private final BackgroundRunMapper mapper;
    private final AgentDurableRunStore runs;
    private final AgentRequestPreparationService preparation;
    private final AgentRunCoordinator coordinator;
    private final ConversationAccessGuard conversations;
    private final ConversationLifecycleMapper lifecycle;
    private final SessionManager sessions;
    private final SecurityContextHelper identity;
    private final ObjectMapper json;
    private final CryptoUtil crypto;
    private final TaskSignalPublisher tasks;
    private final ApprovalEventPublisher events;
    private final com.ai.agent.durable.AgentDurableRuntimeProperties properties;
    private final int eventRetentionDays;

    public BackgroundRunStore(BackgroundRunMapper mapper, AgentDurableRunStore runs,
            AgentRequestPreparationService preparation, AgentRunCoordinator coordinator,
            ConversationAccessGuard conversations, ConversationLifecycleMapper lifecycle, SessionManager sessions,
            SecurityContextHelper identity, ObjectMapper json, CryptoUtil crypto, TaskSignalPublisher tasks,
            ApprovalEventPublisher events, com.ai.agent.durable.AgentDurableRuntimeProperties properties,
            @org.springframework.beans.factory.annotation.Value("${app.agent.background.event-retention-days:7}") int eventRetentionDays) {
        this.mapper = mapper; this.runs = runs; this.preparation = preparation; this.coordinator = coordinator;
        this.conversations = conversations; this.lifecycle = lifecycle; this.sessions = sessions;
        this.identity = identity; this.json = json; this.crypto = crypto; this.tasks = tasks; this.events = events;
        this.properties = properties;
        if (eventRetentionDays < 1 || eventRetentionDays > 90) throw new IllegalArgumentException("事件保留天数须为 1～90");
        this.eventRetentionDays = eventRetentionDays;
    }

    /** 同一用户幂等键重试返回原 Run；会话行锁也阻止同一会话并发创建两次活动执行。 */
    @Transactional
    public Submitted submit(AnalysisRequest input, String key) {
        if (key == null || !key.matches("[A-Za-z0-9_-]{16,64}")) throw new IllegalArgumentException("缺少有效请求幂等键");
        if (input == null || !input.hasSession()) throw new IllegalArgumentException("请先创建会话");
        String tenant = identity.getCurrentTenantId(), user = identity.getCurrentUserId();
        conversations.lockActive(input.getSessionId(), tenant, user);
        String fingerprint = fingerprint(input);
        var existing = mapper.findKey(tenant, user, key).orElse(null);
        if (existing != null) {
            if (!existing.fingerprint().equals(fingerprint)) throw new IllegalArgumentException("幂等键已用于其他请求");
            return new Submitted(existing.runId(), input.getSessionId());
        }
        if (lifecycle.hasPendingRun(input.getSessionId())) throw new IllegalStateException("本会话还有未完成的任务");
        var context = preparation.prepare(input.copy());
        var run = coordinator.prepareSubmission(context);
        runs.create(run);
        mapper.insert(new RunSubmission(run.runId(), tenant, user, key, fingerprint,
                crypto.encrypt(encode(context.getRequest())), false, null, null, 0, 0));
        // 占位轮次与请求一起提交，刷新/重新登录可以找到排队中的 Run；此时不提取长期记忆。
        sessions.saveConversationTurn(run.sessionId(), input.getQuestion(), "任务已提交，等待执行…",
                null, input.getModelId(), 0, 0, run.runId(), MemoryCaptureMode.SKIP, 0);
        tasks.afterCommit(BackgroundTaskConfiguration.RUN_QUEUE, run.runId());
        changed(runs.find(run.runId()).orElseThrow());
        return new Submitted(run.runId(), run.sessionId());
    }

    @Transactional
    public AgentRunStateEntity claim(String id, String owner) {
        var run = runs.find(id).orElse(null);
        if (run == null) return null;
        conversations.lockActive(run.getSessionId(), run.getTenantId(), run.getUserId());
        if (!"CREATED".equals(mapper.lockRun(id).orElse(""))) return null;
        if (mapper.claim(id, owner, leaseMicros()) != 1) return null;
        if (!runs.transition(id, new AgentRunTransition(AgentRunStatus.CREATED, AgentRunStatus.RUNNING,
                AgentRunTerminationReason.NONE, "后台任务已领取"))) throw new IllegalStateException("领取状态冲突");
        changed(run);
        return runs.find(id).orElseThrow();
    }

    /** 围栏同时验证数据库状态和首次执行租约；禁止失租进程写入晚到的模型结果。 */
    public void ensureLease(String id, String owner) {
        if (!mapper.validLease(id, owner)) throw new IllegalStateException("后台执行租约已失效");
    }

    @Transactional
    public boolean renew(String id, String owner) { return mapper.renew(id, owner, leaseMicros()) == 1; }

    private long leaseMicros() { return Math.multiplyExact(properties.getLeaseDuration().toMillis(), 1000L); }

    public boolean isEnabled() { return runs.isEnabled(); }

    @Transactional
    public void append(String id, String owner, String event) {
        var run = runs.find(id).orElseThrow();
        // 和删除会话共用锁序；删除之后不能重新写入敏感事件。
        conversations.lockActive(run.getSessionId(), run.getTenantId(), run.getUserId());
        mapper.lockRun(id).orElseThrow();
        var submission = mapper.lockSubmission(id).orElseThrow();
        if (!Objects.equals(owner, submission.leaseOwner()) || !mapper.validEventLease(id, owner)) {
            throw new IllegalStateException("事件执行租约失效");
        }
        int bytes = event.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > 131072 || mapper.chargeEvent(id, bytes) != 1) throw new IllegalStateException("运行事件超过容量限制");
        mapper.append(id, crypto.encrypt(event));
        changed(run);
    }

    /** 进程断开不重放已开始任务：先原子失败，再保存可读说明。审批现场由原恢复链路负责。 */
    @Transactional
    public void interruptExpired(String id) {
        var run = runs.find(id).orElse(null);
        if (run == null) return;
        boolean readable = conversations.allowContentWrite(run.getSessionId(), run.getTenantId(), run.getUserId());
        mapper.lockRun(id);
        var submission = mapper.lockSubmission(id).orElse(null);
        if (submission == null || !mapper.expiredLease(id)) return;
        if (run.getStatus() == AgentRunStatus.RUNNING && runs.transition(id, new AgentRunTransition(
                AgentRunStatus.RUNNING, AgentRunStatus.FAILED, AgentRunTerminationReason.INTERRUPTED,
                "执行进程已中断，未自动重跑；请检查已执行操作后重新提交"))) {
            if (readable) sessions.updateRunAssistantMessage(run.getSessionId(), run.getTenantId(), run.getUserId(), id,
                    "执行进程已中断。为避免重复操作，任务未自动重跑，请确认操作结果后重新提交。", null, null, 0);
        }
        mapper.release(id, submission.leaseOwner(), eventRetentionDays);
        changed(run);
    }

    @Transactional
    public void finish(String id, String owner) {
        var run = runs.find(id).orElseThrow();
        boolean readable = conversations.allowContentWrite(run.getSessionId(), run.getTenantId(), run.getUserId());
        mapper.lockRun(id);
        var submission = mapper.lockSubmission(id).orElseThrow();
        if (!Objects.equals(owner, submission.leaseOwner())) return;
        if (run.getStatus() == AgentRunStatus.RUNNING) {
            runs.transition(id, new AgentRunTransition(AgentRunStatus.RUNNING, AgentRunStatus.FAILED,
                    AgentRunTerminationReason.FAILED, "后台执行未能完成，请查看运行详情"));
            run = runs.find(id).orElseThrow();
        }
        if (readable && run.getStatus().isTerminal() && run.getStatus() != AgentRunStatus.COMPLETED) {
            sessions.updateRunAssistantMessage(run.getSessionId(), run.getTenantId(), run.getUserId(), id,
                    "任务已结束：" + run.getStatus().name() + "。" + Objects.toString(run.getErrorSummary(), ""), null, null, 0);
        }
        mapper.release(id, owner, eventRetentionDays);
        changed(run);
    }

    /** 复用按小时清理器；执行段已释放才会安排过期，不删除聊天原文或幂等键。 */
    @Transactional
    public void purgeEventLog(String id, int limit) {
        var run = runs.find(id).orElse(null);
        if (run == null) return;
        conversations.allowContentWrite(run.getSessionId(), run.getTenantId(), run.getUserId());
        mapper.lockRun(id);
        var submission = mapper.lockSubmission(id).orElseThrow();
        if (submission.leaseOwner() != null) return;
        mapper.purgeEvents(id, Math.max(1, Math.min(1000, limit)));
        mapper.clearLogExpiry(id);
    }

    public AnalysisRequest request(RunSubmission submission) {
        try { return json.readValue(crypto.decrypt(submission.payload()), AnalysisRequest.class); }
        catch (Exception invalid) { throw new IllegalStateException("后台请求载荷无法读取", invalid); }
    }

    private void changed(AgentRunStateEntity run) {
        events.changed(new ApprovalChanged(run.getTenantId(), run.getUserId(), run.getSessionId(), run.getRunId(), run.getApprovalId()));
    }

    private String encode(AnalysisRequest request) {
        try { return json.writeValueAsString(request); }
        catch (Exception invalid) { throw new IllegalArgumentException("请求无法序列化", invalid); }
    }

    private String fingerprint(AnalysisRequest request) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encode(request).getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    public record Submitted(String runId, String sessionId) { }
}
