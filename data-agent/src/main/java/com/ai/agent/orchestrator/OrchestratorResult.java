package com.ai.agent.orchestrator;

import java.util.Collections;
import java.util.Map;
import com.ai.agent.AgentType;
import com.ai.agent.IntentAnalysisResult;

/**
 * 编排器结构化执行结果，供上层消费完整执行快照。
 *
 * <p>包含最终答案、选中专家、执行计划、共享上下文等。
 * 失败时通过 {@link #failure(String)} 工厂方法构建。</p>
 *
 * @author data-agent
 */
public class OrchestratorResult {

    private final boolean success;
    private final String finalAnswer;
    private final AgentType selectedType;
    private final String selectedAgentName;
    private final OrchestratorExecutionResult executionResult;
    private final OrchestrationPlan orchestrationPlan;
    private final Map<String, Object> sharedContext;
    private final IntentAnalysisResult intentResult;
    private String originalQuery;
    private long durationMs;
    private String error;

    /**
     * 完整构造。
     *
     * @param success 是否执行成功
     * @param finalAnswer 最终答案
     * @param selectedType 选中的 Agent 类型
     * @param selectedAgentName 选中的 Agent 名称
     * @param executionResult 执行结果
     * @param orchestrationPlan 编排计划
     * @param sharedContext 共享上下文
     * @param intentResult 意图分析结果
     */
    public OrchestratorResult(boolean success,
            String finalAnswer,
            AgentType selectedType,
            String selectedAgentName,
            OrchestratorExecutionResult executionResult,
            OrchestrationPlan orchestrationPlan,
            Map<String, Object> sharedContext,
            IntentAnalysisResult intentResult) {
        this.success = success;
        this.finalAnswer = finalAnswer == null ? "" : finalAnswer;
        this.selectedType = selectedType == null ? AgentType.REACT : selectedType;
        this.selectedAgentName = selectedAgentName == null ? "" : selectedAgentName;
        this.executionResult = executionResult;
        this.orchestrationPlan = orchestrationPlan;
        this.sharedContext = sharedContext == null ? Map.of() : Collections.unmodifiableMap(sharedContext);
        this.intentResult = intentResult;
    }

    /**
     * 构建失败结果。
     *
     * @param message 错误信息
     * @return 失败结果
     */
    public static OrchestratorResult failure(String message) {
        return new OrchestratorResult(false, message, AgentType.REACT, "",
                null, null, Map.of(), null);
    }

    /**
     * 是否执行成功。
     *
     * @return 成功返回 true
     */
    public boolean success() {
        return success;
    }

    /**
     * 返回最终答案。
     *
     * @return 最终答案
     */
    public String finalAnswer() {
        return finalAnswer;
    }

    /**
     * 返回选中的 Agent 类型。
     *
     * @return Agent 类型
     */
    public AgentType selectedType() {
        return selectedType;
    }

    /**
     * 返回选中的 Agent 名称。
     *
     * @return Agent 名称
     */
    public String selectedAgentName() {
        return selectedAgentName;
    }

    /**
     * 返回编排执行结果。
     *
     * @return 执行结果
     */
    public OrchestratorExecutionResult executionResult() {
        return executionResult;
    }

    /**
     * 返回编排计划。
     *
     * @return 编排计划
     */
    public OrchestrationPlan orchestrationPlan() {
        return orchestrationPlan;
    }

    /**
     * 返回共享上下文。
     *
     * @return 共享上下文
     */
    public Map<String, Object> sharedContext() {
        return sharedContext;
    }

    /**
     * 返回意图分析结果。
     *
     * @return 意图分析结果
     */
    public IntentAnalysisResult intentResult() {
        return intentResult;
    }

    /**
     * 返回原始查询。
     *
     * @return 原始查询
     */
    public String originalQuery() {
        return originalQuery;
    }

    /**
     * 设置原始查询。
     *
     * @param originalQuery 原始查询
     */
    public void setOriginalQuery(String originalQuery) {
        this.originalQuery = originalQuery;
    }

    /**
     * 返回执行耗时（毫秒）。
     *
     * @return 耗时毫秒数
     */
    public long durationMs() {
        return durationMs;
    }

    /**
     * 设置执行耗时。
     *
     * @param durationMs 耗时毫秒数
     */
    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    /**
     * 返回错误信息。
     *
     * @return 错误信息，无错误时为空
     */
    public String error() {
        return error;
    }

    /**
     * 设置错误信息。
     *
     * @param error 错误信息
     */
    public void setError(String error) {
        this.error = error;
    }
}
