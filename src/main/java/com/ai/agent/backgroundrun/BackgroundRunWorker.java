package com.ai.agent.backgroundrun;

import com.ai.agent.AgentRequestPreparationService;
import com.ai.agent.runtime.AgentRunCoordinator;
import com.ai.agent.runtime.AgentRunRegistry;
import com.ai.agent.runtime.event.AgentSseEventWriter;
import com.ai.background.BackgroundTaskConfiguration;
import com.ai.background.BackgroundUserScope;
import com.ai.background.TaskSignalPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** MQ 唤醒与低频补偿共用领取入口；浏览器订阅不拥有任务或线程句柄。 */
@Component
public class BackgroundRunWorker {
    private static final Logger LOG = LoggerFactory.getLogger(BackgroundRunWorker.class);
    private final BackgroundRunMapper mapper;
    private final BackgroundRunStore store;
    private final BackgroundUserScope users;
    private final AgentRequestPreparationService preparation;
    private final AgentRunCoordinator coordinator;
    private final AgentSseEventWriter writer;
    private final AgentRunRegistry registry;
    private final TaskSignalPublisher signals;
    private final AsyncTaskExecutor executor;
    private final Map<String, String> active = new ConcurrentHashMap<>();

    public BackgroundRunWorker(BackgroundRunMapper mapper, BackgroundRunStore store, BackgroundUserScope users,
            AgentRequestPreparationService preparation, AgentRunCoordinator coordinator, AgentSseEventWriter writer,
            AgentRunRegistry registry, TaskSignalPublisher signals, @Qualifier("agentRunExecutor") AsyncTaskExecutor executor) {
        this.mapper = mapper; this.store = store; this.users = users; this.preparation = preparation;
        this.coordinator = coordinator; this.writer = writer; this.registry = registry; this.signals = signals; this.executor = executor;
    }

    @RabbitListener(queues = BackgroundTaskConfiguration.RUN_QUEUE, concurrency = "1")
    public void receive(Message message) {
        String id = new String(message.getBody(), StandardCharsets.UTF_8);
        if (!id.matches("[a-f0-9-]{36}")) throw new AmqpRejectAndDontRequeueException("无效 Run 引用");
        schedule(id);
    }

    private void schedule(String id) {
        if (!store.isEnabled()) return;
        String owner = UUID.randomUUID().toString();
        if (active.putIfAbsent(id, owner) != null) return;
        try { executor.execute(() -> execute(id, owner)); }
        catch (RuntimeException full) { active.remove(id, owner); }
    }

    private void execute(String id, String owner) {
        boolean claimed = false;
        try {
            var run = store.claim(id, owner);
            if (run == null) return;
            claimed = true;
            var submission = mapper.find(id).orElseThrow();
            var sink = new PersistedRunEventSink(store, writer, id, owner);
            users.call(run.getTenantId(), run.getUserId(), () -> {
                var context = preparation.prepare(store.request(submission));
                return coordinator.executeQueued(context, run, sink, () -> store.ensureLease(id, owner)).join();
            });
            sink.flush();
        } catch (RuntimeException failure) {
            LOG.warn("后台 Run 执行段结束: runId={}, errorType={}", id, failure.getClass().getSimpleName());
        } finally {
            if (claimed) {
                try { store.finish(id, owner); }
                catch (RuntimeException unavailable) { LOG.warn("后台 Run 等待租约补偿: runId={}", id); }
            }
            active.remove(id, owner);
        }
    }

    /** 只对本实例已领取 Run 续租；失败即协作取消，禁止在失租后继续发起工具。 */
    @Scheduled(fixedDelayString = "${app.agent.durable.scan-interval:PT15S}")
    public void renew() {
        active.forEach((id, owner) -> {
            try {
                if (mapper.find(id).filter(RunSubmission::started).isPresent() && !store.renew(id, owner)) {
                    registry.cancelSystem(id, "后台运行租约失效或已取消");
                }
            } catch (RuntimeException unavailable) { registry.cancelSystem(id, "无法确认后台执行租约"); }
        });
    }

    /** 每分钟限量补偿未领取/失租记录，不扫描聊天正文、不重放已开始的任务。 */
    @Scheduled(fixedDelayString = "${app.agent.durable.recovery-interval:PT1M}")
    public void recover() {
        if (!store.isEnabled()) return;
        for (String id : mapper.queued()) signals.send(BackgroundTaskConfiguration.RUN_QUEUE, id);
        for (String id : mapper.expired()) {
            try { store.interruptExpired(id); }
            catch (RuntimeException unavailable) { LOG.warn("后台 Run 补偿暂未完成: runId={}", id); }
        }
    }
}
