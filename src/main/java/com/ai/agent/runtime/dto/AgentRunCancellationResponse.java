package com.ai.agent.runtime.dto;

/**
 * 节点内 Agent Run 取消结果，不暴露其他用户的运行信息。
 *
 * @param success 是否找到当前用户拥有的活跃 Run
 * @param runId 请求取消的运行编号
 * @param status 当前终态
 * @param terminationReason 终止原因
 * @param message 安全提示
 * @author data-agent
 */
public record AgentRunCancellationResponse(
        boolean success,
        String runId,
        String status,
        String terminationReason,
        String message) {
}
