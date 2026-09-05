package com.ai.agent.orchestrator;

/**
 * 并行预执行计划中单个步骤的结果。
 *
 * @param stepId 计划步骤编号
 * @param toolName 实际调用的安全工具
 * @param success 是否执行成功
 * @param result 工具结果或错误信息
 * @author data-agent
 */
public record ParallelPlanStepResult(String stepId, String toolName, boolean success, String result) {

    /**
     * 创建成功的步骤结果。
     *
     * @param step 计划步骤
     * @param toolName 调用的工具名
     * @param result 工具结果
     * @return 成功结果
     */
    public static ParallelPlanStepResult success(ExecutionPlanStep step, String toolName, String result) {
        return new ParallelPlanStepResult(step.id(), toolName, true, result);
    }

    /**
     * 创建失败的步骤结果。
     *
     * @param step 计划步骤
     * @param toolName 调用的工具名
     * @param message 错误信息
     * @return 失败结果
     */
    public static ParallelPlanStepResult failure(ExecutionPlanStep step, String toolName, String message) {
        return new ParallelPlanStepResult(step.id(), toolName, false, message);
    }
}
