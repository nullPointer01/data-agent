package com.ai.agent.dto;

import com.ai.model.AgentExecutionTrace;

import java.time.LocalDateTime;

/**
 * Agent 执行轨迹响应。
 *
 * @param traceId 轨迹编号
 * @param tenantId 租户编号
 * @param userId 用户编号
 * @param sessionId 会话编号
 * @param selectedAgent 选中的 Agent
 * @param selectedType 选中的 Agent 类型
 * @param intent 意图类型
 * @param complexity 任务复杂度
 * @param success 是否成功
 * @param fallbackUsed 是否使用内置回退
 * @param taskCount 任务数量
 * @param durationMs 执行耗时
 * @param question 用户问题
 * @param reason 路由原因
 * @param error 错误信息
 * @param planJson 计划 JSON
 * @param taskResultsJson 任务结果 JSON
 * @param sharedContextJson 共享上下文 JSON
 * @param createdAt 创建时间
 * @author data-agent
 */
public record AgentExecutionTraceResponse(String traceId,
        String tenantId,
        String userId,
        String sessionId,
        String selectedAgent,
        String selectedType,
        String intent,
        String complexity,
        boolean success,
        boolean fallbackUsed,
        int taskCount,
        long durationMs,
        String question,
        String reason,
        String error,
        String planJson,
        String taskResultsJson,
        String sharedContextJson,
        LocalDateTime createdAt) {

    public static AgentExecutionTraceResponse from(AgentExecutionTrace trace) {
        return new AgentExecutionTraceResponse(
                trace.getTraceId(),
                trace.getTenantId(),
                trace.getUserId(),
                trace.getSessionId(),
                trace.getSelectedAgent(),
                trace.getSelectedType(),
                trace.getIntent(),
                trace.getComplexity(),
                trace.isSuccess(),
                trace.isFallbackUsed(),
                trace.getTaskCount(),
                trace.getDurationMs(),
                trace.getQuestion(),
                trace.getReason(),
                trace.getError(),
                trace.getPlanJson(),
                trace.getTaskResultsJson(),
                trace.getSharedContextJson(),
                trace.getCreatedAt());
    }
}
