package com.ai.agent;

import com.ai.model.AnalysisRequest;
import com.ai.model.AnalysisResponse;

import java.util.function.Consumer;

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
     * @param eventEmitter SSE 事件消费者
     */
    void analyzeStreaming(AnalysisRequest request, Consumer<String> eventEmitter);
}
