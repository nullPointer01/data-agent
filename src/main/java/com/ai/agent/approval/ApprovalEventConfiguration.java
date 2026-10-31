package com.ai.agent.approval;

import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/** 每实例独立临时队列广播页面通知；离线恢复以重新查询主库为准，不积压过时通知。 */
@Configuration
public class ApprovalEventConfiguration {
    public static final String EXCHANGE = "data-agent.approval-events";

    @Bean public FanoutExchange approvalEventExchange() { return new FanoutExchange(EXCHANGE); }
    @Bean public AnonymousQueue approvalEventQueue() { return new AnonymousQueue(); }
    @Bean public Binding approvalEventBinding() {
        return BindingBuilder.bind(approvalEventQueue()).to(approvalEventExchange());
    }

    /** 同步 MyBatis 权限复核使用独立有界调度器，不阻塞 MQ 消费和 Reactor 时钟线程。 */
    @Bean(destroyMethod = "dispose")
    public Scheduler approvalEventScheduler() {
        return Schedulers.newBoundedElastic(4, 128, "approval-events");
    }
}
