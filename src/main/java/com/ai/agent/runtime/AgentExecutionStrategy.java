package com.ai.agent.runtime;

import com.ai.model.AnalysisResponse;

/**
 * Chat、ReAct 与 Orchestrated 共用的执行策略合同。
 *
 * @author data-agent
 */
public interface AgentExecutionStrategy {

    /**
     * 返回策略对应的唯一执行模式。
     *
     * @return 执行模式
     */
    AgentExecutionMode mode();

    /**
     * 执行已经确定的 Agent 路线。
     *
     * @param context 运行上下文
     * @param route 路由结果
     * @return 策略执行结果
     */
    Result execute(AgentRunContext context, AgentRunRoute route);

    /**
     * 策略返回的响应、可选追踪细节与过程事件状态。
     *
     * @param response 分析响应
     * @param traceDetail 模式专属追踪细节
     * @param processEventsEmitted 是否已经实时发布过程输出
     */
    record Result(AnalysisResponse response, Object traceDetail, boolean processEventsEmitted) {

        public Result(AnalysisResponse response, boolean processEventsEmitted) {
            this(response, null, processEventsEmitted);
        }
    }
}
