package com.ai.agent.react;

import com.ai.agent.tool.governance.AgentToolExecutionResult;

/**
 * 单轮 ReAct 模型响应处理结果。
 *
 * @param type 结果类型
 * @param answer 未触发工具调用时的清洗后最终答案
 * @param toolName 调用的工具名称
 * @param toolResult 工具调用结果
 * @param finalAnswerRequired 工具结果是否需要立即汇总为最终回答
 * @param recoveryAdvice 可选错误恢复建议
 * @author data-agent
 */
record ReActStepOutcome(ReActStepType type, String answer, String toolName, String toolResult,
                        boolean finalAnswerRequired, ErrorRecoveryAdvice recoveryAdvice,
                        AgentToolExecutionResult executionResult, String approvalId) {

    /**
     * 创建最终回答结果。
     *
     * @param answer 回答文本
     * @return 步骤结果
     */
    static ReActStepOutcome finalAnswer(String answer) {
        return new ReActStepOutcome(ReActStepType.FINAL_ANSWER, answer, null, null, false,
                ErrorRecoveryAdvice.none(), null, null);
    }

    /**
     * 创建工具观察结果。
     *
     * @param toolName 工具名称
     * @param toolResult 工具结果
     * @param finalAnswerRequired 是否立即生成总结
     * @return 步骤结果
     */
    static ReActStepOutcome toolObservation(String toolName, String toolResult, boolean finalAnswerRequired) {
        return toolObservation(toolName, toolResult, finalAnswerRequired, ErrorRecoveryAdvice.none());
    }

    static ReActStepOutcome toolObservation(String toolName, String toolResult, boolean finalAnswerRequired,
            ErrorRecoveryAdvice recoveryAdvice) {
        return new ReActStepOutcome(ReActStepType.TOOL_OBSERVATION, null, toolName, toolResult, finalAnswerRequired,
                recoveryAdvice == null ? ErrorRecoveryAdvice.none() : recoveryAdvice, null, null);
    }

    static ReActStepOutcome toolObservation(AgentToolExecutionResult result, boolean finalAnswerRequired,
            ErrorRecoveryAdvice recoveryAdvice) {
        return new ReActStepOutcome(ReActStepType.TOOL_OBSERVATION, null, result.toolName(),
                result.toModelObservation(), finalAnswerRequired,
                recoveryAdvice == null ? ErrorRecoveryAdvice.none() : recoveryAdvice, result, null);
    }

    /**
     * 创建记忆已索引结果。
     *
     * @return 步骤结果
     */
    static ReActStepOutcome memoryIndexed() {
        return new ReActStepOutcome(ReActStepType.MEMORY_INDEXED, null, null, null, false,
                ErrorRecoveryAdvice.none(), null, null);
    }

    static ReActStepOutcome approvalRequired(AgentToolExecutionResult result, String approvalId) {
        return new ReActStepOutcome(
                ReActStepType.APPROVAL_REQUIRED,
                null,
                result.toolName(),
                null,
                false,
                ErrorRecoveryAdvice.none(),
                result,
                approvalId);
    }

    /**
     * 判断当前结果是否为最终回答。
     *
     * @return 不需要继续执行 ReAct 步骤时返回 true
     */
    boolean isFinalAnswer() {
        return type == ReActStepType.FINAL_ANSWER;
    }

    /**
     * 判断当前结果是否表示记忆已经完成索引。
     *
     * @return 当前逻辑迭代需要重试时返回 true
     */
    boolean isMemoryIndexed() {
        return type == ReActStepType.MEMORY_INDEXED;
    }

    boolean isApprovalRequired() {
        return type == ReActStepType.APPROVAL_REQUIRED;
    }

    /**
     * 判断当前工具观察是否需要自我修正。
     *
     * @return 存在恢复建议时返回 true
     */
    boolean recoveryRequired() {
        return recoveryAdvice != null && recoveryAdvice.recoveryRequired();
    }
}
