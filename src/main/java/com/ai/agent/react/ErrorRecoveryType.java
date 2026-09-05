package com.ai.agent.react;

/**
 * ReAct 自我修正时使用的工具恢复类别。
 *
 * @author data-agent
 */
public enum ErrorRecoveryType {

    /**
     * 工具结果无需特殊恢复指令。
     */
    NONE,

    /**
     * 模型选择了一个不存在的工具。
     */
    UNKNOWN_TOOL,

    /**
     * 必需的工具参数缺失或格式错误。
     */
    ARGUMENT_ERROR,

    /**
     * 数据或知识查询未返回可用数据。
     */
    NO_DATA,

    /**
     * SQL 执行失败，模型应检查 Schema 或简化查询。
     */
    SQL_ERROR,

    /**
     * 上游服务或下游依赖超时。
     */
    TIMEOUT,

    /**
     * 通用工具执行失败。
     */
    TOOL_FAILURE
}
