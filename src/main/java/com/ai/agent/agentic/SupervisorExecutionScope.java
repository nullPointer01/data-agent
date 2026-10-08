package com.ai.agent.agentic;

import com.ai.agent.capability.AgentDelegationGuard.DelegationRejectedException;

import java.util.function.Supplier;

/** 标记整个 Supervisor 调用栈，阻止递归创建管理者和不完整的审批 Checkpoint。 */
public final class SupervisorExecutionScope {
    private static final ThreadLocal<Boolean> ACTIVE = new ThreadLocal<>();

    private SupervisorExecutionScope() { }

    public static boolean active() { return Boolean.TRUE.equals(ACTIVE.get()); }

    /** 异步任务由 TaskContextSnapshot 捕获并恢复此标记，不能污染下一次请求。 */
    public static <T> T call(boolean active, Supplier<T> action) {
        boolean previous = active();
        try {
            restore(active);
            return action.get();
        } finally {
            restore(previous);
        }
    }

    private static void restore(boolean active) {
        if (active) ACTIVE.set(true);
        else ACTIVE.remove();
    }

    /** 尚未持久化整个 Supervisor 图，因此禁止生成只能恢复叶节点的审批现场。 */
    public static void requireApprovalContinuationSupported() {
        if (active()) {
            throw new DelegationRejectedException("SUPERVISOR_APPROVAL_UNSUPPORTED",
                    "Supervisor 暂不支持跨专家审批恢复；本次审批动作未执行，"
                    + "请切换 TOOL_CALLING 并由主助手直接执行审批工具");
        }
    }
}
