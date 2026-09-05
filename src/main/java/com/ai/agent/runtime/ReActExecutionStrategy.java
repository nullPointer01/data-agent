package com.ai.agent.runtime;

import com.ai.agent.react.ReActAgent;
import com.ai.agent.runtime.event.AgentRunEventBridge;
import com.ai.model.AnalysisRequest;
import com.ai.model.AnalysisResponse;
import com.ai.service.MultiAgentRuntimeService;
import org.springframework.stereotype.Component;

/**
 * 执行默认或配置 Agent 的 ReAct 模式。
 *
 * @author data-agent
 */
@Component
public class ReActExecutionStrategy implements AgentExecutionStrategy {

    private final ReActAgent reActAgent;
    private final MultiAgentRuntimeService multiAgentRuntimeService;

    public ReActExecutionStrategy(ReActAgent reActAgent, MultiAgentRuntimeService multiAgentRuntimeService) {
        this.reActAgent = reActAgent;
        this.multiAgentRuntimeService = multiAgentRuntimeService;
    }

    @Override
    public AgentExecutionMode mode() {
        return AgentExecutionMode.REACT;
    }

    @Override
    public Result execute(AgentRunContext context, AgentRunRoute route) {
        AnalysisRequest request = context.executionContext().getRequest();
        String fileContent = context.executionContext().getFileContent();
        if (context.eventSink().isStreaming()) {
            AgentRunEventBridge eventBridge = new AgentRunEventBridge(context);
            AnalysisResponse response = route.profile() == null
                    ? reActAgent.executeStreaming(request, fileContent, eventBridge)
                    : multiAgentRuntimeService.executeStreaming(
                            route.profile(), request, fileContent, eventBridge);
            return new Result(response, true);
        }
        AnalysisResponse response = route.profile() == null
                ? reActAgent.execute(request, fileContent)
                : multiAgentRuntimeService.execute(route.profile(), request, fileContent);
        return new Result(response, false);
    }
}
