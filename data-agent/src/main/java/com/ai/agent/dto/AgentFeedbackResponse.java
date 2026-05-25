package com.ai.agent.dto;

import com.ai.model.AgentFeedback;

import java.time.LocalDateTime;

/**
 * Agent 反馈响应。
 *
 * @param feedbackId 反馈编号
 * @param tenantId 租户编号
 * @param userId 用户编号
 * @param sessionId 会话编号
 * @param traceId 执行轨迹编号
 * @param rating 评分
 * @param question 用户问题
 * @param answer Agent 回答
 * @param comment 备注
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 * @author data-agent
 */
public record AgentFeedbackResponse(String feedbackId,
        String tenantId,
        String userId,
        String sessionId,
        String traceId,
        String rating,
        String question,
        String answer,
        String comment,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static AgentFeedbackResponse from(AgentFeedback feedback) {
        return new AgentFeedbackResponse(
                feedback.getFeedbackId(),
                feedback.getTenantId(),
                feedback.getUserId(),
                feedback.getSessionId(),
                feedback.getTraceId(),
                feedback.getRating(),
                feedback.getQuestion(),
                feedback.getAnswer(),
                feedback.getComment(),
                feedback.getCreatedAt(),
                feedback.getUpdatedAt());
    }
}
