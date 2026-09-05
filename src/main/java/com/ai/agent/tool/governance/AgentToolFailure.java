package com.ai.agent.tool.governance;

/**
 * 已分类且可安全向上层传播的工具失败。
 *
 * @param status 稳定错误码
 * @param retriable 是否为明确瞬时故障
 * @param safeMessage 安全错误说明
 * @author data-agent
 */
public record AgentToolFailure(
        AgentToolExecutionStatus status,
        boolean retriable,
        String safeMessage) {
}
