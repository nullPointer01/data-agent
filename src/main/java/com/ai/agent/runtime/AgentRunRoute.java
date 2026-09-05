package com.ai.agent.runtime;

import com.ai.model.AgentProfile;

/**
 * 一次请求经过确定性解析后的执行目标。
 *
 * @param target 路由目标
 * @param mode Agent 执行模式
 * @param profile 显式 Agent 配置，可为空
 * @author data-agent
 */
public record AgentRunRoute(Target target, AgentExecutionMode mode, AgentProfile profile) {

    /**
     * Agent Runtime 保留的请求目标优先级。
     */
    public enum Target {
        COMMAND,
        CONFIGURED_AGENT,
        SKILL,
        DEFAULT
    }
}
