package com.ai.agent.orchestrator;

import com.ai.model.AnalysisResponse;

import java.util.List;
import com.ai.agent.AgentType;
import com.ai.agent.specialist.SpecialistResult;

/**
 * 编排器执行结果，包含专家任务列表、最终响应和路由元数据。
 *
 * <p>当编排器回退到内置 ReAct 时，{@code fallbackUsed} 为 true。
 * 上层可以通过 {@link #selectedType()} 和 {@link #selectedAgentName()} 获取
 * 被选中的专家类型和名称。</p>
 *
 * @author data-agent
 */
public class OrchestratorExecutionResult {

    private final List<SpecialistResult> taskResults;
    private final AnalysisResponse response;
    private final boolean fallbackUsed;
    private AgentType selectedType;
    private String selectedAgentName;

    /**
     * 简便构造，不设置路由元数据。
     *
     * @param taskResults 专家任务结果列表，可为空
     * @param response 最终分析响应
     * @param fallbackUsed 是否使用了兜底 ReAct
     */
    public OrchestratorExecutionResult(List<SpecialistResult> taskResults,
            AnalysisResponse response,
            boolean fallbackUsed) {
        this.taskResults = taskResults;
        this.response = response;
        this.fallbackUsed = fallbackUsed;
    }

    /**
     * 完整构造，设置路由元数据。
     *
     * @param taskResults 专家任务结果列表，可为空
     * @param response 最终分析响应
     * @param fallbackUsed 是否使用了兜底 ReAct
     * @param selectedType 选中的 Agent 类型
     * @param selectedAgentName 选中的 Agent 名称
     */
    public OrchestratorExecutionResult(List<SpecialistResult> taskResults,
            AnalysisResponse response,
            boolean fallbackUsed,
            AgentType selectedType,
            String selectedAgentName) {
        this.taskResults = taskResults;
        this.response = response;
        this.fallbackUsed = fallbackUsed;
        this.selectedType = selectedType;
        this.selectedAgentName = selectedAgentName;
    }

    /**
     * 返回专家任务结果列表。
     *
     * @return 任务结果列表，可能为空
     */
    public List<SpecialistResult> taskResults() {
        return taskResults;
    }

    /**
     * 返回最终分析响应。
     *
     * @return 分析响应
     */
    public AnalysisResponse response() {
        return response;
    }

    /**
     * 判断是否使用了兜底 ReAct。
     *
     * @return 使用兜底返回 true
     */
    public boolean fallbackUsed() {
        return fallbackUsed;
    }

    /**
     * 返回被选中的 Agent 类型。
     *
     * @return Agent 类型
     */
    public AgentType selectedType() {
        return selectedType;
    }

    /**
     * 设置被选中的 Agent 类型。
     *
     * @param selectedType Agent 类型
     */
    public void setSelectedType(AgentType selectedType) {
        this.selectedType = selectedType;
    }

    /**
     * 返回被选中的 Agent 名称。
     *
     * @return Agent 名称
     */
    public String selectedAgentName() {
        return selectedAgentName;
    }

    /**
     * 设置被选中的 Agent 名称。
     *
     * @param selectedAgentName Agent 名称
     */
    public void setSelectedAgentName(String selectedAgentName) {
        this.selectedAgentName = selectedAgentName;
    }

    private OrchestratorDecision decision;

    /**
     * 返回编排路由决策。
     *
     * @return 路由决策，可为空
     */
    public OrchestratorDecision decision() {
        return decision;
    }

    /**
     * 设置编排路由决策。
     *
     * @param decision 路由决策
     */
    public void setDecision(OrchestratorDecision decision) {
        this.decision = decision;
    }
}
