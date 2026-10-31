package com.ai.agent.approval;

import com.ai.conversation.ConversationAccessGuard;
import com.ai.security.SecurityConstants;
import com.ai.security.SecurityContextHelper;
import com.ai.security.SysUser;
import com.ai.security.SysUserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.access.AccessDeniedException;
import com.ai.security.rbac.RolePermissionService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Scheduler;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;

/** 有界审批 SSE 订阅；通知只用于触发受权查询，不承担持久事件回放。 */
@Service
public class ApprovalEventSubscriptions {
    private final SecurityContextHelper identity;
    private final SysUserRepository users;
    private final RolePermissionService permissions;
    private final ConversationAccessGuard conversations;
    private final ObjectMapper json;
    private final Scheduler scheduler;
    private final Map<String, Subscription> subscriptions = new HashMap<>();

    public ApprovalEventSubscriptions(SecurityContextHelper identity, SysUserRepository users,
            RolePermissionService permissions, ConversationAccessGuard conversations,
            ObjectMapper json, @Qualifier("approvalEventScheduler") Scheduler scheduler) {
        this.identity = identity;
        this.users = users;
        this.permissions = permissions;
        this.conversations = conversations;
        this.json = json;
        this.scheduler = scheduler;
    }

    /** 请求线程捕获身份并校验；异步通知不能依赖线程本地 SecurityContext。 */
    public Flux<ServerSentEvent<String>> open(String sessionId, boolean reviewer) {
        String tenantId = identity.getCurrentTenantId();
        String userId = identity.getCurrentUserId();
        authorize(tenantId, userId, sessionId, reviewer);
        return Flux.defer(() -> {
            Subscription subscription = register(tenantId, userId, sessionId, reviewer);
            Flux<ServerSentEvent<String>> changes = subscription.sink.asFlux()
                    .publishOn(scheduler, 1)
                    .map(change -> {
                        authorize(tenantId, userId, sessionId, reviewer);
                        try {
                            return ServerSentEvent.builder(json.writeValueAsString(Map.of(
                                    "runId", change.runId() == null ? "" : change.runId(),
                                    "approvalId", change.approvalId() == null ? "" : change.approvalId())))
                                    .event("approval_changed").build();
                        } catch (Exception failure) {
                            throw new IllegalStateException("审批通知编码失败", failure);
                        }
                    });
            Flux<ServerSentEvent<String>> heartbeat = Flux.interval(Duration.ofSeconds(15))
                    .map(tick -> ServerSentEvent.<String>builder().comment("heartbeat").build());
            // 先注册后 ready，页面随后读取状态；期间新通知保存在有界队列，不存在先查后订阅的缺口。
            return Flux.concat(Flux.just(ServerSentEvent.builder("{}").event("ready").build()),
                            Flux.merge(1, changes, heartbeat))
                    .take(Duration.ofSeconds(60))
                    .doFinally(signal -> remove(subscription));
        });
    }

    /** MQ 线程仅做内存路由；不查库、不执行审批工具。每个实例都能收到同一广播。 */
    @RabbitListener(queues = "#{approvalEventQueue.name}", concurrency = "1")
    public void receive(Message message) {
        ApprovalChanged change;
        try {
            if (message.getBody().length > 4096) throw new IllegalArgumentException("通知过大");
            change = json.readValue(message.getBody(), ApprovalChanged.class);
            if (change.tenantId() == null || change.userId() == null) throw new IllegalArgumentException("通知身份缺失");
        } catch (Exception invalid) {
            throw new AmqpRejectAndDontRequeueException("无效审批通知", invalid);
        }
        Subscription[] current;
        synchronized (subscriptions) {
            current = subscriptions.values().toArray(Subscription[]::new);
        }
        for (Subscription subscription : current) {
            if (subscription.matches(change)) subscription.offer(change);
        }
    }

    private void authorize(String tenantId, String userId, String sessionId, boolean reviewer) {
        SysUser user = users.findById(userId == null ? "" : userId)
                .filter(SysUser::isEnabled)
                .filter(item -> Objects.equals(tenantId, item.getTenantId()))
                .orElseThrow(() -> new AccessDeniedException("审批订阅无权限"));
        if (reviewer) {
            if (!permissions.hasPermission(user.getRoles(), SecurityConstants.PERMISSION_AGENT_APPROVAL_REVIEW)) {
                throw new AccessDeniedException("审批订阅无权限");
            }
        } else {
            if (sessionId == null || sessionId.isBlank()) throw new IllegalArgumentException("缺少会话标识");
            try {
                conversations.requireReadable(sessionId, tenantId, userId);
            } catch (IllegalArgumentException denied) {
                throw new AccessDeniedException("会话已删除或审批订阅无权限", denied);
            }
        }
    }

    /** 持久 Run 日志分批回放时再次复核用户和来源会话，不依赖异步线程的登录上下文。 */
    public void authorizeOwner(String tenantId, String userId, String sessionId) {
        authorize(tenantId, userId, sessionId, false);
    }

    private Subscription register(String tenantId, String userId, String sessionId, boolean reviewer) {
        synchronized (subscriptions) {
            long owned = subscriptions.values().stream()
                    .filter(item -> item.tenantId.equals(tenantId) && item.userId.equals(userId)).count();
            if (subscriptions.size() >= 512 || owned >= 4) {
                throw new IllegalStateException("审批订阅连接已达上限");
            }
            Subscription subscription = new Subscription(tenantId, userId, sessionId, reviewer);
            subscriptions.put(subscription.id, subscription);
            return subscription;
        }
    }

    private void remove(Subscription subscription) {
        synchronized (subscriptions) { subscriptions.remove(subscription.id); }
    }

    private static final class Subscription {
        private final String id = UUID.randomUUID().toString();
        private final String tenantId;
        private final String userId;
        private final String sessionId;
        private final boolean reviewer;
        private final Sinks.Many<ApprovalChanged> sink = Sinks.many().unicast()
                .onBackpressureBuffer(new ArrayBlockingQueue<>(32));

        private Subscription(String tenantId, String userId, String sessionId, boolean reviewer) {
            this.tenantId = tenantId;
            this.userId = userId;
            this.sessionId = sessionId;
            this.reviewer = reviewer;
        }

        private boolean matches(ApprovalChanged change) {
            return tenantId.equals(change.tenantId()) && ((reviewer && (change.approvalId() != null || change.runId() == null))
                    || (userId.equals(change.userId()) && Objects.equals(sessionId, change.sessionId())));
        }

        private synchronized void offer(ApprovalChanged change) {
            if (sink.tryEmitNext(change).isFailure()) {
                // 慢客户端断开后重新读取当前状态；不可无限积压，也不静默遗漏后一直显示旧状态。
                sink.tryEmitError(new IllegalStateException("审批订阅消费过慢，请重连"));
            }
        }
    }
}
