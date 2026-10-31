package com.ai.background;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Spring AMQP 管理可靠任务队列、持久化消息与失败死信，不自行实现消息客户端。 */
@Configuration
public class BackgroundTaskConfiguration {
    public static final String EXCHANGE = "data-agent.tasks";
    public static final String DEAD_EXCHANGE = "data-agent.tasks.dead";
    public static final String INDEX_QUEUE = "data-agent.index";
    public static final String MEMORY_QUEUE = "data-agent.memory-extraction";
    public static final String RESUME_QUEUE = "data-agent.approval-resume";
    public static final String RUN_QUEUE = "data-agent.run";

    @Bean public DirectExchange taskExchange() { return new DirectExchange(EXCHANGE); }
    @Bean public DirectExchange taskDeadExchange() { return new DirectExchange(DEAD_EXCHANGE); }
    @Bean public Queue indexTaskQueue() { return taskQueue(INDEX_QUEUE); }
    @Bean public Queue memoryTaskQueue() { return taskQueue(MEMORY_QUEUE); }
    @Bean public Queue resumeTaskQueue() { return taskQueue(RESUME_QUEUE); }
    @Bean public Queue runTaskQueue() { return taskQueue(RUN_QUEUE); }
    @Bean public Queue runDeadQueue() { return QueueBuilder.durable(RUN_QUEUE + ".dead").build(); }
    @Bean public Binding runBinding() { return BindingBuilder.bind(runTaskQueue()).to(taskExchange()).with(RUN_QUEUE); }
    @Bean public Binding runDeadBinding() { return BindingBuilder.bind(runDeadQueue()).to(taskDeadExchange()).with(RUN_QUEUE); }
    @Bean public Queue indexDeadQueue() { return QueueBuilder.durable(INDEX_QUEUE + ".dead").build(); }
    @Bean public Queue memoryDeadQueue() { return QueueBuilder.durable(MEMORY_QUEUE + ".dead").build(); }
    @Bean public Queue resumeDeadQueue() { return QueueBuilder.durable(RESUME_QUEUE + ".dead").build(); }
    @Bean public Binding resumeBinding() {
        return BindingBuilder.bind(resumeTaskQueue()).to(taskExchange()).with(RESUME_QUEUE);
    }
    @Bean public Binding resumeDeadBinding() {
        return BindingBuilder.bind(resumeDeadQueue()).to(taskDeadExchange()).with(RESUME_QUEUE);
    }
    @Bean public Binding indexBinding() {
        return BindingBuilder.bind(indexTaskQueue()).to(taskExchange()).with(INDEX_QUEUE);
    }
    @Bean public Binding memoryBinding() {
        return BindingBuilder.bind(memoryTaskQueue()).to(taskExchange()).with(MEMORY_QUEUE);
    }
    @Bean public Binding indexDeadBinding() {
        return BindingBuilder.bind(indexDeadQueue()).to(taskDeadExchange()).with(INDEX_QUEUE);
    }
    @Bean public Binding memoryDeadBinding() {
        return BindingBuilder.bind(memoryDeadQueue()).to(taskDeadExchange()).with(MEMORY_QUEUE);
    }

    private Queue taskQueue(String name) {
        return QueueBuilder.durable(name).quorum()
                .deadLetterExchange(DEAD_EXCHANGE).deadLetterRoutingKey(name).build();
    }
}
