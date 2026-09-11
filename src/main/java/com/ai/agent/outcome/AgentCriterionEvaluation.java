package com.ai.agent.outcome;

import java.util.List;

/**
 * 单条成功标准的可解释评估结果。
 *
 * @param criterionId 成功标准编号
 * @param criterionType 成功标准类型
 * @param required 是否影响整体任务成功
 * @param decision 评估决定
 * @param evaluatorVersion evaluator 版本
 * @param reasonCode 稳定原因码
 * @param evidenceReferences 安全证据引用
 * @author data-agent
 */
public record AgentCriterionEvaluation(
        String criterionId,
        AgentSuccessCriterion.CriterionType criterionType,
        boolean required,
        CriterionDecision decision,
        String evaluatorVersion,
        String reasonCode,
        List<String> evidenceReferences) {

    public AgentCriterionEvaluation {
        evidenceReferences = evidenceReferences == null ? List.of() : List.copyOf(evidenceReferences);
    }

    /**
     * 单条标准的三态决定。
     *
     * @author data-agent
     */
    public enum CriterionDecision {
        PASSED,
        FAILED,
        NOT_EVALUATED
    }
}
