package com.ai.agent.dto;

import java.util.List;

/**
 * Agent 反馈质量洞察响应。
 *
 * @param success 是否成功
 * @param sampleSize 样本数量
 * @param negativeCount 负向反馈总数
 * @param issues 问题分类
 * @param recommendations 全局优化建议
 * @author data-agent
 */
public record AgentFeedbackInsightResponse(boolean success,
        int sampleSize,
        long negativeCount,
        List<AgentFeedbackIssueResponse> issues,
        List<String> recommendations) {
}
