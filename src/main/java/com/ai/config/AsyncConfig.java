package com.ai.config;

import com.ai.agent.tool.governance.AgentToolGovernanceProperties;
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

    @Bean(name = "indexTaskExecutor")
    public Executor indexTaskExecutor(TaskDecorator taskDecorator) {
        return buildExecutor(2, 2, 10, "index-task-", taskDecorator);
    }

    @Bean(name = "sseExecutor")
    public AsyncTaskExecutor sseExecutor(
            @Value("${app.sse.core-pool-size:2}") int corePoolSize,
            @Value("${app.sse.max-pool-size:8}") int maxPoolSize,
            @Value("${app.sse.queue-capacity:100}") int queueCapacity,
            TaskDecorator taskDecorator) {
        return buildExecutor(corePoolSize, maxPoolSize, queueCapacity, "sse-", taskDecorator);
    }

    @Bean(name = "memoryExtractionExecutor")
    public Executor memoryExtractionExecutor(TaskDecorator taskDecorator) {
        return buildExecutor(1, 2, 100, "memory-extraction-", taskDecorator);
    }

    /** MVC 订阅 Flux 后的 Servlet 写入仍可能阻塞，使用独立有界池，避免占用模型或 Run 启动池。 */
    @Bean(name = "mvcStreamingExecutor")
    public AsyncTaskExecutor mvcStreamingExecutor(TaskDecorator taskDecorator) {
        return buildExecutor(2, 8, 100, "mvc-streaming-", taskDecorator);
    }

    /** 终态回调独立于可能等待工具/模型的 SSE worker，避免 ReAct 占满 SSE 池后形成相互等待。 */
    @Bean(name = "modelCompletionExecutor")
    public AsyncTaskExecutor modelCompletionExecutor(TaskDecorator taskDecorator) {
        return buildExecutor(2, 4, 100, "model-completion-", taskDecorator);
    }

    @Bean(name = "modelInvocationExecutor")
    public AsyncTaskExecutor modelInvocationExecutor(TaskDecorator taskDecorator) {
        return buildExecutor(2, 8, 32, "model-invocation-", taskDecorator);
    }

    /**
     * Agent Eval 使用独立有界线程池，避免离线回归任务挤占交互式 Agent 执行资源。
     */
    @Bean(name = "agentEvalExecutor")
    public Executor agentEvalExecutor(
            @Value("${app.agent-eval.core-pool-size:1}") int corePoolSize,
            @Value("${app.agent-eval.max-pool-size:2}") int maxPoolSize,
            @Value("${app.agent-eval.queue-capacity:20}") int queueCapacity,
            TaskDecorator taskDecorator) {
        return buildExecutor(corePoolSize, maxPoolSize, queueCapacity, "agent-eval-", taskDecorator);
    }

    @Bean(name = "agentToolExecutor")
    public AsyncTaskExecutor agentToolExecutor(AgentToolGovernanceProperties properties, TaskDecorator taskDecorator) {
        return buildExecutor(properties.getCorePoolSize(), properties.getMaxPoolSize(),
                properties.getQueueCapacity(), "agent-tool-", taskDecorator);
    }

    /**
     * 委派调用使用独立线程池，避免父委派占用普通 Tool worker 后等待子 Agent 再次调用 Tool。
     */
    @Bean(name = "agentDelegationExecutor")
    public AsyncTaskExecutor agentDelegationExecutor(
            @Value("${app.agent-delegation.core-pool-size:2}") int corePoolSize,
            @Value("${app.agent-delegation.max-pool-size:4}") int maxPoolSize,
            @Value("${app.agent-delegation.queue-capacity:20}") int queueCapacity,
            TaskDecorator taskDecorator) {
        return buildExecutor(corePoolSize, maxPoolSize, queueCapacity, "agent-delegation-", taskDecorator);
    }

    @Bean(name = "agentResumeExecutor")
    public AsyncTaskExecutor agentResumeExecutor(
            @Value("${app.agent.durable.resume-core-pool-size:2}") int corePoolSize,
            @Value("${app.agent.durable.resume-max-pool-size:4}") int maxPoolSize,
            @Value("${app.agent.durable.resume-queue-capacity:100}") int queueCapacity,
            TaskDecorator taskDecorator) {
        return buildExecutor(corePoolSize, maxPoolSize, queueCapacity, "agent-resume-", taskDecorator);
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
