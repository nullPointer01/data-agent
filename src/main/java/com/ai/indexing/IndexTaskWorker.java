package com.ai.indexing;

import com.ai.background.BackgroundTaskConfiguration;
import com.ai.background.TaskLeases;
import com.ai.background.TaskSignalPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** RabbitMQ 分发索引任务，官方监听容器限制并发，主库租约和版本 CAS 保护重复投递。 */
@Component
public class IndexTaskWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(IndexTaskWorker.class);
    private final IndexTaskStore store;
    private final IndexTaskProcessor processor;
    private final TaskSignalPublisher signals;
    private final TaskLeases leases;

    public IndexTaskWorker(IndexTaskStore store, IndexTaskProcessor processor,
            TaskSignalPublisher signals, TaskLeases leases) {
        this.store = store;
        this.processor = processor;
        this.signals = signals;
        this.leases = leases;
    }

    /** 只补偿漏投递、到期重试与失效租约，正常任务提交后立即异步通知 MQ。 */
    @Scheduled(initialDelay = 10000, fixedDelayString = "${app.background-tasks.recovery-delay-ms:60000}")
    public void recover() {
        for (IndexTask task : store.ready(100)) {
            if (!signals.send(BackgroundTaskConfiguration.INDEX_QUEUE, task.id())) break;
        }
    }

    /** 方法成功返回后容器才确认消息，最终失败保留主库记录并进入死信队列。 */
    @RabbitListener(queues = BackgroundTaskConfiguration.INDEX_QUEUE, concurrency = "2")
    public void consume(String taskId) {
        if (taskId == null || !taskId.matches("[a-f0-9]{64}")) {
            throw new AmqpRejectAndDontRequeueException("非法索引任务引用");
        }
        IndexTask candidate = store.findReady(taskId);
        if (candidate == null) return;
        IndexTask task = store.claim(candidate);
        if (task == null) return;
        Throwable failure = null;
        try (var lease = leases.track(() -> store.renew(task))) {
            lease.ensureOwned();
            if (task.attempts() > 5) throw new IllegalStateException("索引任务崩溃恢复次数已耗尽");
            processor.process(task);
            lease.ensureOwned();
        } catch (Exception error) {
            failure = error;
            LOGGER.warn("索引任务失败: taskId={}, version={}, errorType={}",
                    task.id(), task.version(), error.getClass().getSimpleName());
        }
        boolean exhausted = store.finish(task, failure);
        if (store.findReady(task.id()) != null) signals.send(BackgroundTaskConfiguration.INDEX_QUEUE, task.id());
        if (exhausted) throw new AmqpRejectAndDontRequeueException("索引任务重试已耗尽");
    }
}
