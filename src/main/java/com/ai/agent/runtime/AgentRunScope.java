package com.ai.agent.runtime;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * 在当前线程结构化绑定 Agent Run，供深层模型和工具网关读取。
 *
 * @author data-agent
 */
public final class AgentRunScope {

    private static final ThreadLocal<AgentRunContext> CURRENT = new ThreadLocal<>();

    private AgentRunScope() {
    }

    /**
     * 获取当前运行上下文。
     *
     * @return 可选运行上下文
     */
    public static Optional<AgentRunContext> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    /**
     * 获取当前运行上下文；不在统一 Harness 内时拒绝继续执行。
     *
     * @return 当前运行上下文
     */
    public static AgentRunContext requireCurrent() {
        return current().orElseThrow(() -> new IllegalStateException("当前线程没有 Agent Run 作用域"));
    }

    /**
     * 在指定运行上下文中执行并返回结果，退出时恢复上层上下文。
     *
     * @param context 运行上下文
     * @param supplier 执行逻辑
     * @param <T> 返回类型
     * @return 执行结果
     */
    public static <T> T call(AgentRunContext context, Supplier<T> supplier) {
        if (context == null || supplier == null) {
            throw new IllegalArgumentException("Agent Run scope 参数不能为空");
        }
        return bind(context, supplier);
    }

    /** 恢复没有 Run 的任务快照时，显式隔离回调线程上可能残留的另一运行。 */
    public static <T> T without(Supplier<T> supplier) {
        return bind(null, java.util.Objects.requireNonNull(supplier));
    }

    private static <T> T bind(AgentRunContext context, Supplier<T> supplier) {
        AgentRunContext previous = CURRENT.get();
        try {
            restore(context);
            return supplier.get();
        } finally {
            restore(previous);
        }
    }

    /**
     * 在指定运行上下文中执行，退出时恢复上层上下文。
     *
     * @param context 运行上下文
     * @param runnable 执行逻辑
     */
    public static void run(AgentRunContext context, Runnable runnable) {
        call(context, () -> {
            runnable.run();
            return null;
        });
    }

    private static void restore(AgentRunContext previous) {
        if (previous == null) {
            CURRENT.remove();
            return;
        }
        CURRENT.set(previous);
    }
}
