package com.ai.agent.outcome;

import java.util.List;

/**
 * 一次 Agent 任务的聚合结果和逐标准证据。
 *
 * @param status 业务目标状态
 * @param evaluatorId evaluator 标识
 * @param evaluatorVersion evaluator 版本
 * @param reasonCode 聚合原因码
 * @param criteria 逐标准结果
 * @author data-agent
 */
public record AgentOutcomeEvaluation(
        AgentOutcomeStatus status,
        String evaluatorId,
        String evaluatorVersion,
        String reasonCode,
        List<AgentCriterionEvaluation> criteria) {

    public AgentOutcomeEvaluation {
        status = status == null ? AgentOutcomeStatus.NOT_EVALUATED : status;
        criteria = criteria == null ? List.of() : List.copyOf(criteria);
    }
}
