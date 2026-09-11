package com.ai.agent.runtime;

import com.ai.model.AnalysisResponse;
import org.springframework.stereotype.Component;

/**
 * 执行配置化 Agent 的 ReAct 模式。
 *
 * @author data-agent
 */
@Component
final class ReActExecutionStrategy implements AgentExecutionStrategy {

    private final ConfiguredAgentExecutionService configuredAgentExecutionService;

    ReActExecutionStrategy(ConfiguredAgentExecutionService configuredAgentExecutionService) {
        this.configuredAgentExecutionService = configuredAgentExecutionService;
    }

    @Override
    public AgentExecutionMode mode() {
        return AgentExecutionMode.REACT;
    }

    @Override
    public Result execute(AgentRunContext context, AgentRunRoute route) {
        AnalysisResponse response = configuredAgentExecutionService.execute(context, route);
        return new Result(response, context.eventSink().isStreaming());
    }
}
