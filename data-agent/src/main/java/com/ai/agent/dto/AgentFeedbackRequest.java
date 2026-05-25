package com.ai.agent.dto;

/**
 * Agent 回答反馈请求。
 *
 * @param sessionId 会话编号
 * @param traceId 执行轨迹编号
 * @param rating 反馈评分，支持 UP 或 DOWN
 * @param question 用户问题
 * @param answer Agent 回答
 * @param comment 备注
 * @author data-agent
 */
public record AgentFeedbackRequest(String sessionId,
        String traceId,
        String rating,
        String question,
        String answer,
        String comment) {
}
