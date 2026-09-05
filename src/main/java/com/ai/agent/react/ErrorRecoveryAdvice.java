package com.ai.agent.react;

/**
 * 工具观察后生成的恢复建议。
 *
 * @param type 恢复类别
 * @param instruction 给模型读取的恢复指令
 * @param maxAttempts 单次 Agent 运行内的最大恢复次数
 * @author data-agent
 */
public record ErrorRecoveryAdvice(ErrorRecoveryType type, String instruction, int maxAttempts) {

    private static final ErrorRecoveryAdvice NONE = new ErrorRecoveryAdvice(ErrorRecoveryType.NONE, "", 0);

    /**
     * 返回空恢复建议。
     *
     * @return 空建议
     */
    public static ErrorRecoveryAdvice none() {
        return NONE;
    }

    /**
     * 判断建议是否需要追加到 ReAct 观察结果后。
     *
     * @return 指令非空时返回 true
     */
    public boolean hasInstruction() {
        return instruction != null && !instruction.isBlank();
    }

    /**
     * 判断该建议是否应该触发一次恢复。
     *
     * @return 允许重试或反思时返回 true
     */
    public boolean recoveryRequired() {
        return hasInstruction() && maxAttempts > 0;
    }
}
