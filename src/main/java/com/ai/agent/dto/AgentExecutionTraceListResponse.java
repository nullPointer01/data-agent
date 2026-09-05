package com.ai.agent.dto;

import java.util.List;

/**
 * Agent 执行轨迹列表响应。
 *
 * @param success 是否成功
 * @param traces 执行轨迹列表
 * @author data-agent
 */
public record AgentExecutionTraceListResponse(boolean success, List<AgentExecutionTraceResponse> traces) {
}
