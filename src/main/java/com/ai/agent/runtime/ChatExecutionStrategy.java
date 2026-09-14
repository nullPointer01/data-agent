package com.ai.agent.runtime;

import com.ai.model.AnalysisResponse;
import org.springframework.stereotype.Component;

/**
 * 执行配置化 Agent 的 Chat 模式。
 *
 * @author data-agent
 */
@Component
final class ChatExecutionStrategy implements AgentExecutionStrategy {

    private final ConfiguredAgentExecutionService configuredAgentExecutionService;

    ChatExecutionStrategy(ConfiguredAgentExecutionService configuredAgentExecutionService) {
        this.configuredAgentExecutionService = configuredAgentExecutionService;
    }

    @Override
    public AgentExecutionMode mode() {
        return AgentExecutionMode.CHAT;
    }

    @Override
    public Result execute(AgentRunContext context, AgentRunRoute route) {
        AnalysisResponse response = configuredAgentExecutionService.execute(context, route);
        if (response == null) {
            throw new IllegalStateException("Chat 执行结果为空");
        }
        return new Result(response, context.eventSink().isStreaming());
    }
}
