package com.ai.agent.specialist;

import com.ai.model.AnalysisResponse;
import com.ai.agent.AgentExecutionRequest;
import com.ai.agent.AgentType;

/**
 * Agent 专家接口，定义各类型 Agent 的统一执行契约。
 *
 * <p>每个专家实现对应一种 {@link AgentType}，负责处理该类型的执行请求并返回分析结果。</p>
 *
 * @author data-agent
 */
public interface AgentSpecialist {

    /**
     * 返回本专家对应的 Agent 类型。
     *
     * @return Agent 运行类型
     */
    AgentType type();

    /**
     * 执行分析请求并返回结果。
     *
     * @param request 封装了 profile、原始请求、文件内容等信息的执行请求
     * @return 分析结果
     */
    AnalysisResponse execute(AgentExecutionRequest request);
}
