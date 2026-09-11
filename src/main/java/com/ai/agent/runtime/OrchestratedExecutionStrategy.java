package com.ai.agent.runtime;

import com.ai.model.AnalysisResponse;
import org.springframework.stereotype.Component;

/**
 * 执行配置化 Agent 的受控子 Agent 编排模式。
 *
 * @author data-agent
 */
@Component
final class OrchestratedExecutionStrategy implements AgentExecutionStrategy {

    private final ConfiguredAgentExecutionService configuredAgentExecutionService;

    OrchestratedExecutionStrategy(ConfiguredAgentExecutionService configuredAgentExecutionService) {
        this.configuredAgentExecutionService = configuredAgentExecutionService;
    }

    @Override
    public AgentExecutionMode mode() {
        return AgentExecutionMode.ORCHESTRATED;
    }

    @Override
    public Result execute(AgentRunContext context, AgentRunRoute route) {
        AnalysisResponse response = configuredAgentExecutionService.execute(context, route);
        return new Result(response, context.eventSink().isStreaming());
    }
}
