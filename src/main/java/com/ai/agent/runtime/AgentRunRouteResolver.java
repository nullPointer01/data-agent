package com.ai.agent.runtime;

import com.ai.agent.AgentExecutionContext;
import com.ai.agent.orchestrator.OrchestratorAgent;
import com.ai.model.AgentProfile;
import com.ai.model.AnalysisRequest;
import com.ai.service.AgentProfileService;
import com.ai.skill.SkillManager;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * 在不感知同步或流式传输方式的情况下解析 Agent 执行路线。
 *
 * @author data-agent
 */
@Component
public class AgentRunRouteResolver {

    private final AgentProfileService agentProfileService;
    private final SkillManager skillManager;
    private final OrchestratorAgent orchestratorAgent;

    public AgentRunRouteResolver(AgentProfileService agentProfileService,
            SkillManager skillManager,
            @Nullable OrchestratorAgent orchestratorAgent) {
        this.agentProfileService = agentProfileService;
        this.skillManager = skillManager;
        this.orchestratorAgent = orchestratorAgent;
    }

    /**
     * 按现有优先级解析请求目标与执行模式。
     *
     * @param executionContext 已准备的请求上下文
     * @return 确定的执行路线
     */
    public AgentRunRoute resolve(AgentExecutionContext executionContext) {
        AnalysisRequest request = executionContext.getRequest();
        if (request.isCommand() && skillManager.canProcessCommand(request.getQuestion())) {
            return new AgentRunRoute(AgentRunRoute.Target.COMMAND, AgentExecutionMode.CHAT, null);
        }
        if (request.hasAgent()) {
            AgentProfile profile = agentProfileService.requireEnabledAgent(request.getAgentId());
            AgentExecutionMode mode = profile.isChatMode() ? AgentExecutionMode.CHAT : AgentExecutionMode.REACT;
            return new AgentRunRoute(AgentRunRoute.Target.CONFIGURED_AGENT, mode, profile);
        }
        if (request.hasSkill()) {
            return new AgentRunRoute(AgentRunRoute.Target.SKILL, AgentExecutionMode.CHAT, null);
        }
        AgentExecutionMode defaultMode = orchestratorAgent == null
                ? AgentExecutionMode.REACT
                : AgentExecutionMode.ORCHESTRATED;
        return new AgentRunRoute(AgentRunRoute.Target.DEFAULT, defaultMode, null);
    }
}
