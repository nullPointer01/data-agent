package com.ai.agent;

/**
 * Agent 路由器使用的粗粒度任务复杂度。
 *
 * @author data-agent
 */
public enum TaskComplexity {

    /**
     * 可以直接回答的简单闲聊或概念解释请求。
     */
    SIMPLE,

    /**
     * 至少需要一个工具、文件、数据源或业务上下文查询的请求。
     */
    TOOL_ASSISTED,

    /**
     * 意图或上下文较复杂，需要进入完整推理循环的请求。
     */
    COMPLEX
}
