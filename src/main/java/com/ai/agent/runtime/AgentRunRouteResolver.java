package com.ai.agent.runtime;

import com.ai.agent.AgentExecutionContext;
import com.ai.model.AgentProfile;
import com.ai.model.AnalysisRequest;
import com.ai.agent.runtime.planning.RequestExecutionPlan;
import com.ai.service.AgentProfileService;
import com.ai.skill.SkillManager;
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
    private final PersonalAgentExecutionModeResolver personalModeResolver;

    public AgentRunRouteResolver(AgentProfileService agentProfileService,
            SkillManager skillManager,
            PersonalAgentExecutionModeResolver personalModeResolver) {
        this.agentProfileService = agentProfileService;
        this.skillManager = skillManager;
        this.personalModeResolver = personalModeResolver;
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
            RequestExecutionPlan plan = RequestExecutionPlan.explicitRoute("斜杠命令已由命令路由精确匹配");
            return AgentRunRoute.command(plan);
        }
        if (request.hasAgent()) {
            AgentProfile profile = agentProfileService.requireEnabledAgent(request.getAgentId());
            RequestExecutionPlan plan = personalModeResolver.plan(profile, executionContext);
            return AgentRunRoute.configured(profile, plan);
        }
        if (request.hasSkill()) {
            RequestExecutionPlan plan = RequestExecutionPlan.explicitRoute("请求显式指定 Skill，由 Skill 路由执行");
            return AgentRunRoute.skill(plan);
        }
        AgentProfile personalAgent = agentProfileService.requireMyDefaultAgent();
        RequestExecutionPlan plan = personalModeResolver.plan(personalAgent, executionContext);
        return AgentRunRoute.configured(personalAgent, plan);
    }
}
