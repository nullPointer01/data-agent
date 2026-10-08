package com.ai.mcp;

import com.ai.config.TaskContextSnapshot;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import com.ai.agent.runtime.AgentRunControl;

/** 模型流用共享定时器检查取消/超时，完成动作交给独立有界执行器，不占线程等待网络。 */
@Component
public class ModelStreamRuntime {
    private final ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1, task -> {
        Thread thread = new Thread(task, "model-stream-deadline");
        thread.setDaemon(true);
        return thread;
    });
    private final AsyncTaskExecutor completionExecutor;
    private final Set<ModelResponseStream> activeStreams = ConcurrentHashMap.newKeySet();
    private boolean closed;

    public ModelStreamRuntime(@Qualifier("modelCompletionExecutor") AsyncTaskExecutor completionExecutor) {
        this.completionExecutor = completionExecutor;
        scheduler.setRemoveOnCancelPolicy(true);
    }

    /** 应用停止时让所有未完成的模型流进入终态，不能只停掉超时检查。 */
    synchronized ModelResponseStream open(Consumer<String> consumer, AgentRunControl control) {
        if (closed) {
            throw new CancellationException("模型流运行时已关闭");
        }
        ModelResponseStream stream = new ModelResponseStream(consumer, control, scheduler);
        activeStreams.add(stream);
        stream.completion().whenComplete((response, error) -> activeStreams.remove(stream));
        return stream;
    }

    Executor callbacks(TaskContextSnapshot snapshot) {
        return snapshot.executor(action -> {
            try {
                completionExecutor.execute(action);
            } catch (java.util.concurrent.RejectedExecutionException rejected) {
                // 终态清理不能因池已满丢失；背压时由当前回调线程执行，但身份仍由 snapshot 恢复。
                action.run();
            }
        });
    }

    @PreDestroy
    public void close() {
        ModelResponseStream[] streams;
        synchronized (this) {
            closed = true;
            streams = activeStreams.toArray(ModelResponseStream[]::new);
        }
        for (ModelResponseStream stream : streams) {
            stream.onError(new CancellationException("应用停止，模型流已取消"));
        }
        scheduler.shutdownNow();
    }
}
