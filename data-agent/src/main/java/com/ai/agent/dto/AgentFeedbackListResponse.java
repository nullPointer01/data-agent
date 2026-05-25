package com.ai.agent.dto;

import java.util.List;

/**
 * Agent 反馈列表响应。
 *
 * @param success 是否成功
 * @param feedbacks 反馈列表
 * @author data-agent
 */
public record AgentFeedbackListResponse(boolean success, List<AgentFeedbackResponse> feedbacks) {
}
