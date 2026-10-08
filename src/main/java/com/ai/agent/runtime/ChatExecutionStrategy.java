package com.ai.agent.runtime;

import com.ai.model.AnalysisResponse;
import org.springframework.stereotype.Component;
import java.util.concurrent.CompletableFuture;

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
        return executeAsync(context, route).join();
    }

    @Override
    public CompletableFuture<Result> executeAsync(AgentRunContext context, AgentRunRoute route) {
        return configuredAgentExecutionService.executeAsync(context, route).thenApply(response -> new Result(response, context.eventSink().isStreaming()));
    }
}
