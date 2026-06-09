package com.ai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

import org.springframework.core.task.AsyncTaskExecutor;

/**
 * 异步任务执行器配置。
 *
 * @author data-agent
 */
@Configuration
public class AsyncConfig {

    @Bean
    public TaskDecorator contextPropagatingTaskDecorator() {
        return new ContextPropagatingTaskDecorator();
    }

    @Bean(name = "fileProcessingExecutor")
    public Executor fileProcessingExecutor(
            @Value("${app.file-processing.core-pool-size:2}") int corePoolSize,
            @Value("${app.file-processing.max-pool-size:4}") int maxPoolSize,
            @Value("${app.file-processing.queue-capacity:100}") int queueCapacity,
            TaskDecorator taskDecorator) {
        return buildExecutor(corePoolSize, maxPoolSize, queueCapacity, "file-processing-", taskDecorator);
    }

    @Bean(name = "knowledgeVectorExecutor")
    public Executor knowledgeVectorExecutor(
            @Value("${app.knowledge-vector.core-pool-size:1}") int corePoolSize,
            @Value("${app.knowledge-vector.max-pool-size:2}") int maxPoolSize,
            @Value("${app.knowledge-vector.queue-capacity:100}") int queueCapacity,
            TaskDecorator taskDecorator) {
        return buildExecutor(corePoolSize, maxPoolSize, queueCapacity, "knowledge-vector-", taskDecorator);
    }

    @Bean(name = "sseExecutor")
    public AsyncTaskExecutor sseExecutor(
            @Value("${app.sse.core-pool-size:2}") int corePoolSize,
            @Value("${app.sse.max-pool-size:8}") int maxPoolSize,
            @Value("${app.sse.queue-capacity:100}") int queueCapacity,
            TaskDecorator taskDecorator) {
        return buildExecutor(corePoolSize, maxPoolSize, queueCapacity, "sse-", taskDecorator);
    }

    @Bean(name = "agentTaskExecutor")
    public Executor agentTaskExecutor(
            @Value("${app.agent-task.core-pool-size:2}") int corePoolSize,
            @Value("${app.agent-task.max-pool-size:6}") int maxPoolSize,
            @Value("${app.agent-task.queue-capacity:100}") int queueCapacity,
            TaskDecorator taskDecorator) {
        return buildExecutor(corePoolSize, maxPoolSize, queueCapacity, "agent-task-", taskDecorator);
    }

    private ThreadPoolTaskExecutor buildExecutor(int corePoolSize, int maxPoolSize, int queueCapacity,
            String threadNamePrefix, TaskDecorator taskDecorator) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setTaskDecorator(taskDecorator);
        executor.initialize();
        return executor;
    }
}
