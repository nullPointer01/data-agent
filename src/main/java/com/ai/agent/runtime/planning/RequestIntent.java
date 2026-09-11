package com.ai.agent.runtime.planning;

/**
 * 请求规划层使用的用户目标分类。
 *
 * <p>这里描述的是本次请求需要什么执行资源，不替代业务领域中的具体实体识别。</p>
 *
 * @author data-agent
 */
public enum RequestIntent {

    /** 询问 Agent 身份或已配置能力，可由 Harness 本地回答。 */
    CAPABILITY_INTRODUCTION,

    /** 普通交流，不依赖私有知识或工具。 */
    GENERAL_CONVERSATION,

    /** 通用概念解释，直接使用模型知识。 */
    GENERAL_KNOWLEDGE,

    /** 需要个人知识库证据的问题。 */
    PERSONAL_KNOWLEDGE,

    /** 处理本次请求携带或明确提及的文件。 */
    FILE_ANALYSIS,

    /** 查询或分析结构化数据。 */
    DATA_QUERY,

    /** 请求命中当前 Agent 已绑定且可用的 Skill。 */
    SKILL_TASK,

    /** 可能产生外部副作用的动作请求。 */
    ACTION,

    /** 包含多个目标或需要协作拆解的任务。 */
    MULTI_STEP,

    /** 命令、指定 Skill 等已经由上层显式确定的路线。 */
    EXPLICIT_ROUTE
}
