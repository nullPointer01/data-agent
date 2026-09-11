package com.ai.agent.runtime;

import com.ai.agent.AgentExecutionContext;
import com.ai.agent.runtime.planning.PersonalAgentRequestPlanner;
import com.ai.agent.runtime.planning.RequestExecutionPlan;
import com.ai.model.AgentProfile;
import org.springframework.stereotype.Component;

/**
 * 个人 Agent 请求规划的运行时入口。
 *
 * <p>调用方必须传递完整计划，不能只取 mode 后重新使用兼容执行路径。</p>
 */
@Component
public class PersonalAgentExecutionModeResolver {

    private final PersonalAgentRequestPlanner requestPlanner;

    public PersonalAgentExecutionModeResolver(PersonalAgentRequestPlanner requestPlanner) {
        this.requestPlanner = requestPlanner;
    }

    /**
     * 返回 AUTO 与固定模式共用的一次请求级执行计划。
     */
    public RequestExecutionPlan plan(AgentProfile profile, AgentExecutionContext context) {
        return requestPlanner.plan(profile, context);
    }
}
