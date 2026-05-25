package com.ai.agent.react;

/**
 * ReAct 循环执行后的结果。
 *
 * @param answer 最终答案
 * @param iterations 实际执行轮数
 * @author data-agent
 */
public record ReActExecutionResult(String answer, int iterations) {
}
