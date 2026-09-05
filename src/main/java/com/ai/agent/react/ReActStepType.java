package com.ai.agent.react;

/**
 * ReAct 步骤结果类型。
 *
 * @author data-agent
 */
enum ReActStepType {
    /**
     * 模型未请求工具，直接生成最终回答。
     */
    FINAL_ANSWER,

    /**
     * 模型请求工具，工具结果已作为观察信息追加。
     */
    TOOL_OBSERVATION,

    /**
     * 工具动作已持久化，循环必须暂停等待人工审批。
     */
    APPROVAL_REQUIRED,

    /**
     * 工具已写入记忆，循环应重试同一个逻辑迭代。
     */
    MEMORY_INDEXED
}
