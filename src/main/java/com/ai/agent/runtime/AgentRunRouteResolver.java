package com.ai.agent.runtime;

import com.ai.agent.AgentExecutionContext;
import com.ai.model.AgentProfile;
import com.ai.model.AnalysisRequest;
import com.ai.agent.runtime.planning.RequestExecutionPlan;
import com.ai.service.AgentProfileService;
import org.springframework.stereotype.Component;

/**
 * 在不感知同步或流式传输方式的情况下解析 Agent 执行路线。
 *
 * @author data-agent
 */
@Component
public class AgentRunRouteResolver {

    private final AgentProfileService agentProfileService;
    private final PersonalAgentExecutionModeResolver personalModeResolver;

    public AgentRunRouteResolver(AgentProfileService agentProfileService,
            PersonalAgentExecutionModeResolver personalModeResolver) {
        this.agentProfileService = agentProfileService;
        this.personalModeResolver = personalModeResolver;
    }

    /** 只解析并鉴权配置，不调用模型；Run 创建后再进行语义规划。 */
    public AgentProfile resolveProfile(AgentExecutionContext executionContext) {
        AnalysisRequest request = executionContext.getRequest();
        return request.hasAgent() ? agentProfileService.requireEnabledAgent(request.getAgentId())
                : agentProfileService.requireMyDefaultAgent();
    }

    public AgentRunRoute plan(AgentProfile profile, AgentExecutionContext executionContext) {
        RequestExecutionPlan plan = personalModeResolver.plan(profile, executionContext);
        return AgentRunRoute.configured(profile, plan);
    }
}
