package com.ai.memory;

import com.ai.background.BackgroundTaskConfiguration;
import com.ai.background.BackgroundUserScope;
import com.ai.background.TaskLeases;
import com.ai.background.TaskSignalPublisher;
import com.ai.repository.ConversationSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 持久记忆提取消费者，模型在事务外调用，提交时重验来源、租约和修订号。 */
@Component
public class MemoryExtractionWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(MemoryExtractionWorker.class);
    private final MemoryExtractionTaskStore tasks;
    private final SemanticMemoryExtractor extractor;
    private final SemanticMemoryService memories;
    private final BackgroundUserScope identity;
    private final ConversationSessionRepository sessions;
    private final TaskSignalPublisher signals;
    private final TaskLeases leases;
    private final MemoryWriteGuard writes;

    public MemoryExtractionWorker(MemoryExtractionTaskStore tasks, SemanticMemoryExtractor extractor,
            SemanticMemoryService memories, BackgroundUserScope identity, ConversationSessionRepository sessions,
            TaskSignalPublisher signals, TaskLeases leases, MemoryWriteGuard writes) {
        this.tasks = tasks;
        this.extractor = extractor;
        this.memories = memories;
        this.identity = identity;
        this.sessions = sessions;
        this.signals = signals;
        this.leases = leases;
        this.writes = writes;
    }

    /** 正常投递不扫描，每分钟补偿尚未完成的持久任务。 */
    @Scheduled(initialDelay = 10000, fixedDelayString = "${app.background-tasks.recovery-delay-ms:60000}")
    public void recover() {
        for (MemoryExtractionTask task : tasks.ready()) {
            if (!signals.send(BackgroundTaskConfiguration.MEMORY_QUEUE, task.id())) break;
        }
    }

    /** AUTO 表示监听方法成功返回后确认，不是收到消息立即确认。 */
    @RabbitListener(queues = BackgroundTaskConfiguration.MEMORY_QUEUE, concurrency = "1")
    public void consume(String taskId) {
        if (taskId == null || !taskId.matches("[a-f0-9-]{36}")) {
            throw new AmqpRejectAndDontRequeueException("非法记忆任务引用");
        }
        MemoryExtractionTask task = tasks.claim(taskId);
        if (task == null) return;
        Throwable failure = null;
        try (var lease = leases.track(() -> tasks.renew(task))) {
            lease.ensureOwned();
            if (task.attempts() > 5) throw new IllegalStateException("记忆任务崩溃恢复次数已耗尽");
            if (writes.current(task) && sessions.findBySessionIdAndUserIdAndTenantId(
                    task.sessionId(), task.userId(), task.tenantId())
                    .filter(session -> "ACTIVE".equals(session.getStatus()) || "ARCHIVED".equals(session.getStatus())).isPresent()) {
                identity.call(task.tenantId(), task.userId(), () -> {
                    var input = tasks.input(task);
                    var candidates = extractor.extract(input.userMessage(), input.assistantReply(), task.modelId());
                    lease.ensureOwned();
                    // 模型可能耗时较长，写入前再次加载当前账号及权限。
                    int count = identity.call(task.tenantId(), task.userId(),
                            () -> memories.applyBackground(task, candidates));
                    LOGGER.info("持久记忆提取已处理: taskId={}, count={}", task.id(), count);
                    return count;
                });
            }
            lease.ensureOwned();
        } catch (Exception error) {
            failure = error;
            LOGGER.warn("持久记忆提取失败: taskId={}, errorType={}", task.id(), error.getClass().getSimpleName());
        }
        if (tasks.finish(task, failure)) throw new AmqpRejectAndDontRequeueException("记忆提取重试已耗尽");
    }

    /** 成功任务只保留去重记录，失败任务保留用于排查。 */
    @Scheduled(fixedDelay = 3600000)
    public void purge() { tasks.purge(); }
}
