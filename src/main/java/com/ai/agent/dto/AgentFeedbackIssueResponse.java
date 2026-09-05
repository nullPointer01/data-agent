package com.ai.agent.dto;

import java.util.List;

/**
 * Agent 反馈问题分类响应。
 *
 * @param issueType 问题类型
 * @param issueName 问题名称
 * @param count 命中数量
 * @param ratio 样本占比
 * @param recommendation 优化建议
 * @param samples 样本反馈
 * @author data-agent
 */
public record AgentFeedbackIssueResponse(String issueType,
        String issueName,
        long count,
        double ratio,
        String recommendation,
        List<AgentFeedbackResponse> samples) {
}
