package com.ai.agent.durable;

import com.ai.agent.runtime.AgentRunStatus;
import com.ai.agent.runtime.AgentRunTerminationReason;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 一次受状态机约束的 Run 转换请求。
 *
 * @param expectedStatus 期望当前状态
 * @param targetStatus 目标状态
 * @param reason 稳定终止原因
 * @param detail 安全状态摘要
 * @author data-agent
 */
public record AgentRunTransition(
        AgentRunStatus expectedStatus,
        AgentRunStatus targetStatus,
        AgentRunTerminationReason reason,
        String detail) {

    private static final Set<AgentRunStatus> ACTIVE_TERMINALS = EnumSet.of(
            AgentRunStatus.COMPLETED,
            AgentRunStatus.FAILED,
            AgentRunStatus.CANCELLED,
            AgentRunStatus.TIMED_OUT,
            AgentRunStatus.BUDGET_EXHAUSTED);

    private static final Map<AgentRunStatus, Set<AgentRunStatus>> ALLOWED = Map.of(
            AgentRunStatus.CREATED, union(EnumSet.of(AgentRunStatus.RUNNING), ACTIVE_TERMINALS),
            AgentRunStatus.RUNNING, union(EnumSet.of(AgentRunStatus.WAITING_APPROVAL), ACTIVE_TERMINALS),
            AgentRunStatus.WAITING_APPROVAL, EnumSet.of(
                    AgentRunStatus.RESUMING,
                    AgentRunStatus.REJECTED,
                    AgentRunStatus.EXPIRED,
                    AgentRunStatus.CANCELLED,
                    AgentRunStatus.FAILED),
            AgentRunStatus.RESUMING, union(EnumSet.of(AgentRunStatus.WAITING_APPROVAL), ACTIVE_TERMINALS));

    public AgentRunTransition {
        if (expectedStatus == null || targetStatus == null || reason == null) {
            throw new IllegalArgumentException("Agent Run 状态转换字段不能为空");
        }
        if (!ALLOWED.getOrDefault(expectedStatus, Set.of()).contains(targetStatus)) {
            throw new IllegalArgumentException("非法 Agent Run 状态转换: "
                    + expectedStatus + " -> " + targetStatus);
        }
        if (targetStatus.isTerminal() && reason == AgentRunTerminationReason.NONE) {
            throw new IllegalArgumentException("Agent Run 终态必须提供稳定终止原因");
        }
        if (!targetStatus.isTerminal() && reason != AgentRunTerminationReason.NONE) {
            throw new IllegalArgumentException("Agent Run 非终态不能携带终止原因");
        }
        detail = detail == null ? "" : detail;
    }

    private static Set<AgentRunStatus> union(Set<AgentRunStatus> left, Set<AgentRunStatus> right) {
        EnumSet<AgentRunStatus> result = EnumSet.copyOf(left);
        result.addAll(right);
        return result;
    }
}
