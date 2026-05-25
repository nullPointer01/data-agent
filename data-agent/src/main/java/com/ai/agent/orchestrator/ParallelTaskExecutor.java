package com.ai.agent.orchestrator;

import com.ai.config.ContextPropagatingTaskDecorator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskDecorator;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * 在保留安全上下文的前提下并行执行独立的 Agent 任务。
 *
 * <p>Agent 子任务通常依赖 Spring Security 里的租户、用户和请求链路日志信息。这个包装器
 * 通过统一任务装饰器恢复上下文。</p>
 *
 * @author data-agent
 */
@Component
public class ParallelTaskExecutor {

    private final Executor executor;
    private final TaskDecorator taskDecorator;

    @Autowired
    public ParallelTaskExecutor(@Qualifier("agentTaskExecutor") Executor executor) {
        this(executor, new ContextPropagatingTaskDecorator());
    }

    ParallelTaskExecutor(Executor executor, TaskDecorator taskDecorator) {
        this.executor = executor;
        this.taskDecorator = taskDecorator;
    }

    /**
     * 使用当前请求上下文启动异步任务。
     *
     * @param supplier 任务提供器
     * @param <T> 结果类型
     * @return Future 结果
     */
    public <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Runnable task = taskDecorator.decorate(() -> completeFuture(supplier, future));
        executor.execute(task);
        return future;
    }

    private <T> void completeFuture(Supplier<T> supplier, CompletableFuture<T> future) {
        try {
            future.complete(supplier.get());
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
    }
}
