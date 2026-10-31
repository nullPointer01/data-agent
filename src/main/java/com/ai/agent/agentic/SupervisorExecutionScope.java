package com.ai.agent.agentic;

import java.util.function.Supplier;

/** 传播 Supervisor 节点现场，阻止递归管理者，并在审批前校验完整恢复边界。 */
public final class SupervisorExecutionScope {
    private static final ThreadLocal<SupervisorCheckpointState> CURRENT = new ThreadLocal<>();

    private SupervisorExecutionScope() { }

    public static boolean active() { return CURRENT.get() != null; }

    /** 捕获的是本次串行执行现场引用，不能跨 Run 使用。 */
    public static SupervisorCheckpointState current() { return CURRENT.get(); }

    /** 异步任务由 TaskContextSnapshot 捕获并恢复此标记，不能污染下一次请求。 */
    public static <T> T call(SupervisorCheckpointState state, Supplier<T> action) {
        var previous = CURRENT.get();
        try {
            restore(state);
            return action.get();
        } finally {
            restore(previous);
        }
    }

    private static void restore(SupervisorCheckpointState state) {
        if (state != null) CURRENT.set(state);
        else CURRENT.remove();
    }

    /** 普通工具审批没有协作现场；Supervisor 审批必须同时保存直接节点与父决策事实。 */
    public static SupervisorCheckpoint capture(String executorId) {
        return active() ? CURRENT.get().capture(executorId) : null;
    }
}
