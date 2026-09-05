package com.ai.agent.react;

/**
 * 单次 ReAct 循环步骤的控制结果。
 *
 * @param shouldContinue 循环是否继续
 * @param memoryIndexed 当前步骤是否只写入记忆且不消耗迭代次数
 * @param recoveryRequired 当前步骤是否触发错误恢复尝试
 * @param suspended 当前步骤是否持久化暂停
 * @param approvalId 当前审批ID
 * @author data-agent
 */
record ReActLoopStepResult(
        boolean shouldContinue,
        boolean memoryIndexed,
        boolean recoveryRequired,
        boolean suspended,
        String approvalId) {

    ReActLoopStepResult(boolean shouldContinue, boolean memoryIndexed, boolean recoveryRequired) {
        this(shouldContinue, memoryIndexed, recoveryRequired, false, null);
    }

    ReActLoopStepResult(boolean shouldContinue, boolean memoryIndexed) {
        this(shouldContinue, memoryIndexed, false, false, null);
    }

    static ReActLoopStepResult suspended(String approvalId) {
        return new ReActLoopStepResult(false, false, false, true, approvalId);
    }
}
