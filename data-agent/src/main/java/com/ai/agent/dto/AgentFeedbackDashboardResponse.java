package com.ai.agent.dto;

/**
 * Agent 反馈管理台聚合响应。
 *
 * @param success 是否成功
 * @param summary 反馈摘要
 * @param insights 反馈洞察
 * @param feedbacks 反馈列表
 * @author data-agent
 */
public record AgentFeedbackDashboardResponse(boolean success,
        AgentFeedbackSummaryResponse summary,
        AgentFeedbackInsightResponse insights,
        AgentFeedbackListResponse feedbacks) {
}
