package com.ai.agent.capability;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * 在线程内绑定当前一层 Agent 的能力快照，并在嵌套调用结束后恢复父 Agent 边界。
 *
 * @author data-agent
 */
public final class AgentCapabilityScope {

    private static final ThreadLocal<AgentCapabilityBindingSnapshot> CURRENT = new ThreadLocal<>();

    private AgentCapabilityScope() {
    }

    /**
     * 获取当前 Agent 能力快照。
     *
     * @return 当前线程绑定的快照
     */
    public static Optional<AgentCapabilityBindingSnapshot> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    /**
     * 在指定能力边界内执行并返回结果。
     *
     * @param snapshot 当前层不可变能力快照
     * @param supplier 执行逻辑
     * @param <T> 结果类型
     * @return 执行结果
     */
    public static <T> T call(AgentCapabilityBindingSnapshot snapshot, Supplier<T> supplier) {
        if (snapshot == null || supplier == null) {
            throw new IllegalArgumentException("Agent 能力作用域参数不能为空");
        }
        AgentCapabilityBindingSnapshot previous = CURRENT.get();
        try {
            CURRENT.set(snapshot);
            return supplier.get();
        } finally {
            restore(previous);
        }
    }

    /**
     * 在指定能力边界内执行。
     *
     * @param snapshot 当前层不可变能力快照
     * @param runnable 执行逻辑
     */
    public static void run(AgentCapabilityBindingSnapshot snapshot, Runnable runnable) {
        call(snapshot, () -> {
            runnable.run();
            return null;
        });
    }

    private static void restore(AgentCapabilityBindingSnapshot previous) {
        if (previous == null) {
            CURRENT.remove();
            return;
        }
        CURRENT.set(previous);
    }
}
