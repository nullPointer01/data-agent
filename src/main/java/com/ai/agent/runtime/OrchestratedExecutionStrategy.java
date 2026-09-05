package com.ai.agent.runtime;

import com.ai.agent.orchestrator.OrchestratorAgent;
import com.ai.agent.orchestrator.OrchestratorResult;
import com.ai.model.AnalysisRequest;
import com.ai.model.AnalysisResponse;
import org.springframework.stereotype.Component;

/**
 * 执行默认的多专家编排模式。
 *
 * @author data-agent
 */
@Component
public class OrchestratedExecutionStrategy implements AgentExecutionStrategy {

    private final OrchestratorAgent orchestratorAgent;

    public OrchestratedExecutionStrategy(OrchestratorAgent orchestratorAgent) {
        this.orchestratorAgent = orchestratorAgent;
    }

    @Override
    public AgentExecutionMode mode() {
        return AgentExecutionMode.ORCHESTRATED;
    }

    @Override
    public Result execute(AgentRunContext context, AgentRunRoute route) {
        AnalysisRequest request = context.executionContext().getRequest();
        OrchestratorResult result = orchestratorAgent.executeStructured(
                request,
                context.executionContext().getFileContent(),
                context.executionContext().getSession());
        if (result == null) {
            return new Result(AnalysisResponse.fail("编排器未返回执行结果"), null, false);
        }
        AnalysisResponse response = result.executionResult() == null
                ? AnalysisResponse.fail(result.error() == null ? result.finalAnswer() : result.error())
                : result.executionResult().response();
        if (response == null) {
            response = AnalysisResponse.fail("编排器未返回分析响应");
        }
        if (result.finalAnswer() != null && !result.finalAnswer().isBlank()) {
            response.setResult(result.finalAnswer());
        }
        response.setSuccess(result.success() && response.isSuccess());
        return new Result(response, result, false);
    }
}
