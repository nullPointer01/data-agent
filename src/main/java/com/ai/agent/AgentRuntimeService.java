package com.ai.agent;

import com.ai.agent.runtime.AgentRunCoordinator;
import com.ai.agent.runtime.event.AgentEventSink;
import com.ai.model.AnalysisResponse;
import org.springframework.stereotype.Service;

/**
 * Agent Runtime 的应用入口，具体治理统一委托给 Run Coordinator。
 *
 * @author data-agent
 */
@Service
public class AgentRuntimeService {

    private final AgentRunCoordinator runCoordinator;

    public AgentRuntimeService(AgentRunCoordinator runCoordinator) {
        this.runCoordinator = runCoordinator;
    }

    /**
     * 同步执行一次已经准备好的分析请求。
     *
     * @param context 执行上下文
     * @return 带 Run 元数据的响应
     */
    public AnalysisResponse execute(AgentExecutionContext context) {
        return runCoordinator.execute(context);
    }

    /**
     * 使用传输无关事件接收端执行流式请求。
     *
     * @param context 执行上下文
     * @param eventSink 事件接收端
     * @return 完整响应
     */
    public AnalysisResponse executeStreaming(AgentExecutionContext context, AgentEventSink eventSink) {
        return runCoordinator.execute(context, eventSink);
    }
}
