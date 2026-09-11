package com.ai.agent;

import org.springframework.util.StringUtils;

/**
 * ReAct 工具型 Agent 的 System Prompt 组装器。
 *
 * <p>把 System Prompt 拆成两层：</p>
 * <ul>
 *   <li><b>框架基座</b>（{@link #REACT_FRAMEWORK_BASE}）：所有 react 模式 Agent 共享的工具调用纪律与防幻觉规则，
 *       与角色无关，框架统一注入，写一次。</li>
 *   <li><b>角色层</b>：每个 Agent 不同——默认 Agent 用 {@link #DEFAULT_DATA_ANALYST_ROLE}，
 *       用户配置的 Agent 用其 profile 里自定义的 systemPrompt。</li>
 * </ul>
 *
 * <p>这样框架该保证的（如"直接发起原生工具调用，不要只用文字描述意图"）所有 Agent 自动具备，
 * 角色该决定的完全交给配置，避免把框架规则和具体角色糊在一个硬编码字符串里。</p>
 *
 * @author data-agent
 */
public final class AgentSystemPrompts {

    /**
     * 框架基座：所有 react 模式 Agent 共享的工具调用纪律。
     *
     * <p>第 1、2 条专门规避"intent without action"（模型只用文字说"我去查一下"却不发起真正的
     * function call，导致 ReAct 循环误判收尾）这一常见失败模式。</p>
     */
    public static final String REACT_FRAMEWORK_BASE = """
            ## 工作准则
            - 需要使用工具时，直接发起原生工具调用，不要只用文字描述你打算调用工具（例如不要只说"我先查询一下"却不实际调用）。
            - 工具声明需要管理员审批时，审批由服务端在工具调用后自动创建；参数齐全且用户已明确要求执行时直接调用工具，不要在聊天中要求用户再次确认。只有缺少工具必填参数时才询问缺失信息。
            - 一次只推进一步：需要多个工具时，先调用第一个，拿到结果后再决定下一步。
            - 绝不编造数据。没有数据就调用工具查询，或请求用户补充。
            - 需要数学计算时使用 calculate 工具，不要心算。
            - 系统可能提供"检索上下文"（来自知识库的企业资料），优先基于它回答；不足时再调用工具或说明需要补充资料。
            - 已获得足够信息时，直接给出最终回答（使用 Markdown 格式，结构清晰、专业简洁），不要再调用工具。
            """;

    /** 默认 ReAct Agent 的角色层。 */
//    public static final String DEFAULT_DATA_ANALYST_ROLE =
//            "你是一个企业数据分析智能体，擅长结合企业数据、知识库和多种工具完成数据分析任务。";
//    public static final String DEFAULT_DATA_ANALYST_ROLE =
//            "你是一个只用英文回答的酒店分析助手，所有回复必须是英文。";
    public static final String DEFAULT_DATA_ANALYST_ROLE =
            "你是一个数据分析助手。重要规则：每一条回复都必须以「🦜播报：」四个字开头。";

    /** 默认 ReAct Agent 的完整 System Prompt（角色 + 框架基座）。 */
    public static final String DEFAULT_REACT = compose(DEFAULT_DATA_ANALYST_ROLE);

    private AgentSystemPrompts() {
    }

    /**
     * 用角色层 + 框架基座组装完整 System Prompt。
     *
     * @param role 角色描述，为空时回退默认数据分析角色
     * @return 完整 System Prompt
     */
    public static String compose(String role) {
        String resolvedRole = StringUtils.hasText(role) ? role.strip() : DEFAULT_DATA_ANALYST_ROLE;
        return resolvedRole + "\n\n" + REACT_FRAMEWORK_BASE;
    }
}
