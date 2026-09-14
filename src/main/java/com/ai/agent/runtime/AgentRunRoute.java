package com.ai.agent.runtime;

import com.ai.model.AgentProfile;
import com.ai.agent.runtime.planning.RequestExecutionPlan;

/**
 * 一次请求经过确定性解析后的执行目标。
 *
 * @param target 路由目标
 * @param mode Agent 执行模式
 * @param profile 配置化路线的 Agent 配置
 * @param requestPlan 模型调用前固化的请求执行计划
 * @author data-agent
 */
public record AgentRunRoute(
        Target target,
        AgentExecutionMode mode,
        AgentProfile profile,
        RequestExecutionPlan requestPlan) {

    public AgentRunRoute {
        if (target == null || mode == null || requestPlan == null) {
            throw new IllegalArgumentException("Agent 路线的目标、模式和请求计划不能为空");
        }
        if (requestPlan.mode() != mode) {
            throw new IllegalArgumentException("Agent 路线模式与请求计划不一致");
        }
        if (target == Target.CONFIGURED_AGENT && profile == null) {
            throw new IllegalArgumentException("配置化 Agent 路线必须包含 AgentProfile");
        }
    }

    public static AgentRunRoute configured(AgentProfile profile, RequestExecutionPlan requestPlan) {
        if (requestPlan == null) {
            throw new IllegalArgumentException("配置化 Agent 路线缺少请求计划");
        }
        return new AgentRunRoute(Target.CONFIGURED_AGENT, requestPlan.mode(), profile, requestPlan);
    }

    /**
     * Agent Runtime 的配置化执行目标。
     */
    public enum Target {
        CONFIGURED_AGENT
    }
}
