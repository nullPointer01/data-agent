package com.ai.agent.approval;

import com.ai.agent.durable.AgentRunStateEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.Executor;

/** 只在数据库事务提交成功后广播；通知失败不改变已经提交的审批决定。 */
@Component
public class ApprovalEventPublisher {
    private static final Logger LOG = LoggerFactory.getLogger(ApprovalEventPublisher.class);
    private final ApplicationEventPublisher events;
    private final RabbitTemplate rabbit;
    private final ObjectMapper json;
    private final Executor executor;

    public ApprovalEventPublisher(ApplicationEventPublisher events, RabbitTemplate rabbit, ObjectMapper json,
            @Qualifier("backgroundSignalExecutor") Executor executor) {
        this.events = events;
        this.rabbit = rabbit;
        this.json = json;
        this.executor = executor;
    }

    /** 从当前审批 Run 登记刷新信号，不把此时的状态快照当作最终结果。 */
    public void changed(AgentRunStateEntity run) {
        changed(new ApprovalChanged(run.getTenantId(), run.getUserId(), run.getSessionId(),
                run.getRunId(), run.getApprovalId()));
    }

    /** 删除会话可使用空 Run/审批引用，让管理员更新列表、所有者停止读取已删除内容。 */
    public void changed(ApprovalChanged change) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("审批变更通知必须登记在写事务内");
        }
        events.publishEvent(change);
    }

    /** 外部发送移到提交后的独立有界池；漏通知由客户端重连时重新读取状态收敛。 */
    @TransactionalEventListener
    public void committed(ApprovalChanged change) {
        try {
            executor.execute(() -> send(change));
        } catch (RuntimeException rejected) {
            LOG.warn("审批通知发送池已满，等待页面重连同步");
        }
    }

    private void send(ApprovalChanged change) {
        try {
            MessageProperties properties = new MessageProperties();
            properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            properties.setDeliveryMode(MessageDeliveryMode.NON_PERSISTENT);
            // 广播提示无需持久化；业务决定和执行结果已保存于 MySQL。
            rabbit.send(ApprovalEventConfiguration.EXCHANGE, "",
                    new Message(json.writeValueAsBytes(change), properties));
        } catch (Exception failure) {
            LOG.warn("审批通知未发送，等待页面重连同步: errorType={}", failure.getClass().getSimpleName());
        }
    }
}
