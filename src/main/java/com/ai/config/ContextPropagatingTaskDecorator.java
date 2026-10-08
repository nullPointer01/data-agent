package com.ai.config;

import org.springframework.core.task.TaskDecorator;

/** 线程池任务和模型回调共用同一个上下文快照实现。 */
public class ContextPropagatingTaskDecorator implements TaskDecorator {
    @Override
    public Runnable decorate(Runnable runnable) {
        return TaskContextSnapshot.capture().wrap(runnable);
    }
}
