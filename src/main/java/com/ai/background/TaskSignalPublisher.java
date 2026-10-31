package com.ai.background;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.Executor;

/** 数据库任务是恢复依据；发送失败不能回滚已提交的来源，也不能将任务误标成功。 */
@Component
public class TaskSignalPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskSignalPublisher.class);
    private final ApplicationEventPublisher events;
    private final RabbitTemplate rabbit;
    private final Executor executor;

    public TaskSignalPublisher(ApplicationEventPublisher events, RabbitTemplate rabbit,
            @Qualifier("backgroundSignalExecutor") Executor executor) {
        this.events = events;
        this.rabbit = rabbit;
        this.executor = executor;
    }

    /** 在来源事务中登记通知；回滚不会触发发送，外部网络调用不占用请求事务。 */
    public void afterCommit(String queue, String taskId) {
        events.publishEvent(new TaskReady(queue, taskId));
    }

    /** 单独有界发送池；拒绝时仍可由低频持久任务补偿恢复。 */
    @TransactionalEventListener
    public void committed(TaskReady signal) {
        try {
            executor.execute(() -> send(signal.queue(), signal.taskId()));
        } catch (RuntimeException rejected) {
            LOGGER.warn("任务通知等待持久补偿: queue={}, errorType={}", signal.queue(), rejected.getClass().getSimpleName());
        }
    }

    /** 供消费者收敛新版本和低频补偿调用；MQ 只接收任务引用。 */
    public boolean send(String queue, String taskId) {
        if (!BackgroundTaskConfiguration.INDEX_QUEUE.equals(queue)
                && !BackgroundTaskConfiguration.MEMORY_QUEUE.equals(queue)
                && !BackgroundTaskConfiguration.RESUME_QUEUE.equals(queue)
                && !BackgroundTaskConfiguration.RUN_QUEUE.equals(queue)) {
            throw new IllegalArgumentException("未知后台任务队列");
        }
        try {
            MessageProperties properties = new MessageProperties();
            properties.setContentType(MessageProperties.CONTENT_TYPE_TEXT_PLAIN);
            properties.setContentEncoding(StandardCharsets.UTF_8.name());
            properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            properties.setMessageId(UUID.randomUUID().toString());
            CorrelationData confirmation = new CorrelationData(properties.getMessageId());
            confirmation.getFuture().whenComplete((confirm, error) -> {
                if (error != null || confirm == null || !confirm.isAck() || confirmation.getReturned() != null) {
                    LOGGER.warn("任务发布未确认，保留主库任务等待补偿: queue={}, taskId={}", queue, taskId);
                }
            });
            rabbit.send(BackgroundTaskConfiguration.EXCHANGE, queue,
                    new Message(taskId.getBytes(StandardCharsets.UTF_8), properties), confirmation);
            return true;
        } catch (RuntimeException error) {
            LOGGER.warn("任务队列暂不可达，保留主库任务: queue={}, errorType={}", queue, error.getClass().getSimpleName());
            return false;
        }
    }
}
