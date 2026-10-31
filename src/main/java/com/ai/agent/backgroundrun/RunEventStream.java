package com.ai.agent.backgroundrun;

import com.ai.agent.approval.ApprovalEventSubscriptions;
import com.ai.agent.durable.AgentDurableRunStore;
import com.ai.agent.durable.AgentRunStateEntity;
import com.ai.conversation.ConversationAccessGuard;
import com.ai.security.SecurityContextHelper;
import com.ai.util.CryptoUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** 先订阅广播再按游标读持久日志；MQ 漏通知由一分钟重连收敛，断连从不取消 Run。 */
@Service
public class RunEventStream {
    private final BackgroundRunMapper mapper;
    private final AgentDurableRunStore runs;
    private final ApprovalEventSubscriptions changes;
    private final SecurityContextHelper identity;
    private final ConversationAccessGuard conversations;
    private final ObjectMapper json;
    private final CryptoUtil crypto;
    private final Scheduler scheduler;

    public RunEventStream(BackgroundRunMapper mapper, AgentDurableRunStore runs, ApprovalEventSubscriptions changes,
            SecurityContextHelper identity, ConversationAccessGuard conversations, ObjectMapper json,
            CryptoUtil crypto, @Qualifier("approvalEventScheduler") Scheduler scheduler) {
        this.mapper = mapper; this.runs = runs; this.changes = changes; this.identity = identity;
        this.conversations = conversations; this.json = json; this.crypto = crypto; this.scheduler = scheduler;
    }

    public Flux<ServerSentEvent<String>> open(String id, long after) {
        if (after < 0) throw new IllegalArgumentException("无效事件游标");
        String tenant = identity.getCurrentTenantId(), user = identity.getCurrentUserId();
        var run = owned(id, tenant, user);
        if (mapper.find(id).isEmpty()) throw new IllegalArgumentException("此 Run 不支持事件续读");
        var notifications = changes.open(run.getSessionId(), false);
        return Flux.defer(() -> {
            var cursor = new AtomicLong(after);
            // open 内部先注册，再发 ready。重放期间新通知进入原有有界队列。
            return notifications.publishOn(scheduler, 1).concatMap(notice -> {
                if (notice.data() == null) return Flux.just(notice);
                return replay(id, cursor, tenant, user, run.getSessionId())
                        .concatWith(Flux.defer(() -> Flux.just(state(owned(id, tenant, user)))));
            }, 1).take(java.time.Duration.ofSeconds(60));
        });
    }

    private Flux<ServerSentEvent<String>> replay(String id, AtomicLong cursor, String tenant, String user, String session) {
        return Flux.<ServerSentEvent<String>, ArrayDeque<RunEventRow>>generate(ArrayDeque::new, (page, output) -> {
            if (page.isEmpty()) {
                changes.authorizeOwner(tenant, user, session);
                page.addAll(mapper.events(id, cursor.get()));
            }
            if (page.isEmpty()) { output.complete(); return page; }
            var row = page.removeFirst();
            cursor.set(row.id());
            output.next(ServerSentEvent.builder(crypto.decrypt(row.payload())).id(Long.toString(row.id()))
                    .event("stream").build());
            return page;
        });
    }

    private AgentRunStateEntity owned(String id, String tenant, String user) {
        var run = runs.findOwned(id, tenant, user).orElseThrow(() -> new IllegalArgumentException("运行不存在或无权限"));
        conversations.requireReadable(run.getSessionId(), tenant, user);
        return run;
    }

    private ServerSentEvent<String> state(AgentRunStateEntity run) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "run_state"); body.put("runId", run.getRunId()); body.put("mode", run.getMode().name());
        body.put("status", run.getStatus().name()); body.put("terminationReason", run.getTerminationReason().name());
        body.put("approvalId", run.getApprovalId()); body.put("content", run.getErrorSummary());
        var submission = mapper.find(run.getRunId()).orElseThrow();
        body.put("segmentFinished", submission.leaseOwner() == null
                && (submission.started() || run.getStatus().isTerminal()));
        try { return ServerSentEvent.builder(json.writeValueAsString(body)).event("run_state").build(); }
        catch (Exception invalid) { throw new IllegalStateException("运行状态无法编码", invalid); }
    }
}
