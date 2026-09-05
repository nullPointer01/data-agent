package com.ai.agent.react;

/**
 * ReAct 循环执行后的结果。
 *
 * @param answer 最终答案
 * @param iterations 实际执行轮数
 * @param toolCallCount 实际发起的工具调用次数（用于执行轨迹统计）
 * @param success 是否正常完成（模型调用失败 / 达到最大迭代仍计成功，仅空响应标记失败）
 * @param suspended 是否等待人工审批
 * @param approvalId 当前审批ID
 * @author data-agent
 */
public record ReActExecutionResult(
        String answer,
        int iterations,
        int toolCallCount,
        boolean success,
        boolean suspended,
        String approvalId) {

    public ReActExecutionResult(String answer, int iterations, int toolCallCount, boolean success) {
        this(answer, iterations, toolCallCount, success, false, null);
    }

    /**
     * 向后兼容构造：同步路径 {@code run()} 暂不统计工具调用次数与成败标记。
     *
     * @param answer 最终答案
     * @param iterations 实际执行轮数
     */
    public ReActExecutionResult(String answer, int iterations) {
        this(answer, iterations, 0, true, false, null);
    }
}
