package com.ai.agent.dto;

/**
 * Agent 反馈变更响应。
 *
 * @param success 是否成功
 * @param message 响应消息
 * @param feedbackId 反馈编号
 * @author data-agent
 */
public record AgentFeedbackMutationResponse(boolean success, String message, String feedbackId) {

    public static AgentFeedbackMutationResponse saved(String feedbackId) {
        return new AgentFeedbackMutationResponse(true, "反馈已记录", feedbackId);
    }
}
