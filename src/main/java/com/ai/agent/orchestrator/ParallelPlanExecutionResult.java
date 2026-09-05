package com.ai.agent.orchestrator;

import java.util.List;

/**
 * 并行计划预执行的聚合结果。
 *
 * @param stepResults 单个步骤的执行结果
 * @author data-agent
 */
public record ParallelPlanExecutionResult(List<ParallelPlanStepResult> stepResults) {

    public ParallelPlanExecutionResult {
        stepResults = stepResults == null ? List.of() : List.copyOf(stepResults);
    }

    /**
     * 判断是否存在预执行结果。
     *
     * @return 结果列表非空时返回 true
     */
    public boolean hasResults() {
        return !stepResults.isEmpty();
    }

    /**
     * 将结果格式化为可注入提示词的文本。
     *
     * @return 提示词文本
     */
    public String toPromptContent() {
        if (stepResults.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("以下是可并行预检步骤的执行结果，后续推理请优先复用这些结果，避免重复调用相同工具:\n");
        for (ParallelPlanStepResult result : stepResults) {
            builder.append("- [").append(result.stepId()).append("] ")
                    .append(result.toolName())
                    .append(result.success() ? " 成功: " : " 失败: ")
                    .append(result.result())
                    .append('\n');
        }
        return builder.toString().trim();
    }
}
