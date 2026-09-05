package com.ai.agent;

import com.ai.model.AnalysisRequest;
import com.ai.model.AnalysisResponse;
import com.ai.agent.runtime.event.AgentEventSink;

/**
 * 数据分析 Agent 统一入口。
 *
 * @author data-agent
 */
public interface DataAnalysisAgent {

    /**
     * 分析用户请求并返回 Agent 响应。
     *
     * @param request 分析请求
     * @return 分析响应
     */
    AnalysisResponse analyze(AnalysisRequest request);

    /**
     * 流式分析：事件实时推送到 emitter，结束后返回会话相关信息。
     *
     * @param request 分析请求
     * @param eventSink 传输无关事件接收端
     * @return 完整分析响应
     */
    AnalysisResponse analyzeStreaming(AnalysisRequest request, AgentEventSink eventSink);
}
