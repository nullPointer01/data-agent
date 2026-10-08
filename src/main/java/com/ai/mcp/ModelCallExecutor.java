package com.ai.mcp;

import com.ai.agent.runtime.AgentRunControl;
import com.ai.config.TaskContextSnapshot;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** 同步模型协议在独立有界池执行，调用方仍可按 Run 截止时间和取消状态结束等待。 */
@Component
public class ModelCallExecutor {
    private final AsyncTaskExecutor executor;

    public ModelCallExecutor(@Qualifier("modelInvocationExecutor") AsyncTaskExecutor executor) {
        this.executor = executor;
    }

    public <T> T call(Callable<T> action, AgentRunControl control) throws Exception {
        if (control == null) {
            return action.call();
        }
        control.ensureActive();
        TaskContextSnapshot snapshot = TaskContextSnapshot.capture();
        var task = executor.submit(() -> snapshot.call(() -> {
            try {
                control.ensureActive();
                return action.call();
            } catch (Exception error) {
                throw new CompletionException(error);
            }
        }));
        try {
            while (true) {
                control.ensureActive();
                try {
                    return task.get(Math.max(1L, Math.min(250L, control.remainingTime().toMillis())),
                            TimeUnit.MILLISECONDS);
                } catch (TimeoutException waiting) {
                    // 只检查当前请求，不重新发起模型调用。
                }
            }
        } catch (ExecutionException failure) {
            Throwable cause = failure.getCause();
            while (cause instanceof CompletionException && cause.getCause() != null) {
                cause = cause.getCause();
            }
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw new IllegalStateException("模型执行失败", cause);
        } finally {
            if (!task.isDone()) {
                // SDK 的底层中断是尽力操作；有界池保证不无限放大仍在等待的调用。
                task.cancel(true);
            }
        }
    }
}
