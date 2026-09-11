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
 * @param selectedType 运行模式；字段名为历史兼容名称
 * @param intent 意图类型
 * @param complexity 历史复杂度字段，新 Run 不再写入
 * @param success 是否成功
 * @param fallbackUsed 是否使用工具候选兜底
 * @param taskCount 工具调用数量
 * @param durationMs 执行耗时
 * @param question 用户问题
 * @param reason 路由原因
 * @param error 错误信息
 * @param planJson 计划 JSON
 * @param taskResultsJson 历史任务结果 JSON，新 Run 固定为空数组
 * @param sharedContextJson Run 证据 JSON，包含工具、上下文和结果治理摘要
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
