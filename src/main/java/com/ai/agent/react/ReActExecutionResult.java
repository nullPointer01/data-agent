package com.ai.agent.react;

/**
 * 框架工具对话或审批续跑的执行结果。
 *
 * @param answer 最终答案
 * @param iterations 实际执行轮数
 * @param toolCallCount 实际发起的工具调用次数（用于执行轨迹统计）
 * @param success 是否正常完成（审批暂停单独标记，模型失败和预算耗尽向上抛出）
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

}
