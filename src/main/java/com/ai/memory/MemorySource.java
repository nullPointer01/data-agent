package com.ai.memory;

/**
 * 记忆条目的产生来源。
 *
 * @author data-agent
 */
public enum MemorySource {

    /**
     * 用户显式请求记住的记忆。
     */
    USER_EXPLICIT,

    /**
     * 从用户消息中隐式推断的记忆。
     */
    USER_IMPLICIT,

    /**
     * Agent 执行过程中提取的记忆。
     */
    AGENT_EXTRACTED,

    /**
     * 系统摘要或维护任务生成的记忆。
     */
    SYSTEM_GENERATED
}
