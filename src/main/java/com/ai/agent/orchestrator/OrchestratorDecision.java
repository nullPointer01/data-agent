package com.ai.agent.orchestrator;

import com.ai.model.AgentProfile;
import com.ai.agent.AgentType;
import com.ai.agent.IntentAnalysisResult;
import com.ai.agent.TaskClassification;

/**
 * 编排器路由决策结果。
 *
 * <p>包含任务分类、意图分析、编排计划和最终选择的专家类型，
 * 供上层消费决策过程的完整快照。</p>
 *
 * @param classification 任务复杂度分类
 * @param intentResult 意图分析结果
 * @param plan 编排计划，可为空
 * @param selectedProfile 匹配到的 Agent 配置，可为空
 * @param selectedType 最终选择的 Agent 类型
 * @param routingReason 路由原因说明
 * @author data-agent
 */
public record OrchestratorDecision(TaskClassification classification,
        IntentAnalysisResult intentResult,
        OrchestrationPlan plan,
        AgentProfile selectedProfile,
        AgentType selectedType,
        String routingReason) {

    public OrchestratorDecision {
        selectedType = selectedType == null ? AgentType.REACT : selectedType;
        routingReason = routingReason == null ? "" : routingReason;
    }

    public String reason() {
        return routingReason;
    }

    public IntentAnalysisResult intentAnalysis() {
        return intentResult;
    }

    public OrchestrationPlan orchestrationPlan() {
        return plan;
    }
}
