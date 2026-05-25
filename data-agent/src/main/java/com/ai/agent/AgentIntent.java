package com.ai.agent;

/**
 * Orchestrator 路由前识别出的用户高层意图。
 *
 * @author data-agent
 */
public enum AgentIntent {

    /** 简单对话或问候，不需要工具。 */
    GENERAL_CHAT,

    /** 数据源、指标、SQL、图表或上传文件分析请求。 */
    DATA_ANALYSIS,

    /** 企业文档、知识库或制度检索请求。 */
    KNOWLEDGE_RETRIEVAL,

    /** 报告、总结、模板或技能类生成请求。 */
    REPORT_GENERATION,

    /** 需要多步工具编排、应继续留在 ReAct 的请求。 */
    TOOL_ORCHESTRATION,

    /** 没有更强专家信号的开放式推理请求。 */
    COMPLEX_REASONING
}
