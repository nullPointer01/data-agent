package com.ai.agent.dto;

/**
 * Agent 反馈质量摘要。
 *
 * @param success 是否成功
 * @param totalCount 总反馈数
 * @param upCount 正向反馈数
 * @param downCount 负向反馈数
 * @param positiveRate 正向反馈率
 * @param latestNegative 最近一条负向反馈
 * @author data-agent
 */
public record AgentFeedbackSummaryResponse(boolean success,
        long totalCount,
        long upCount,
        long downCount,
        double positiveRate,
        AgentFeedbackResponse latestNegative) {
}
