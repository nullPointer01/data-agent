package com.ai.agent.orchestrator;

/**
 * ReAct 循环开始前的单个计划步骤。
 *
 * @param id 稳定步骤编号
 * @param objective 步骤目标
 * @param suggestedTool 可选的工具提示
 * @param parallelizable 该步骤是否可以后续并行执行
 * @param phase 执行阶段，从 1 开始
 * @author data-agent
 */
public record ExecutionPlanStep(String id, String objective, String suggestedTool, boolean parallelizable, int phase) {

    public ExecutionPlanStep(String id, String objective, String suggestedTool, boolean parallelizable) {
        this(id, objective, suggestedTool, parallelizable, parallelizable ? 1 : 2);
    }

    public ExecutionPlanStep {
        id = defaultIfBlank(id, "step");
        objective = defaultIfBlank(objective, "执行任务");
        suggestedTool = suggestedTool == null ? "" : suggestedTool;
        phase = Math.max(1, phase);
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }
}
