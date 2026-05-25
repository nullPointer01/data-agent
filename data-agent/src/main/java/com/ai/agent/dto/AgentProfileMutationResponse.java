package com.ai.agent.dto;

/**
 * Agent 配置变更响应。
 *
 * @author data-agent
 */
public record AgentProfileMutationResponse(
        boolean success,
        String message,
        String agentId,
        Boolean enabled) {

    public static AgentProfileMutationResponse saved(String agentId) {
        return new AgentProfileMutationResponse(true, "Agent 已保存", agentId, null);
    }

    public static AgentProfileMutationResponse toggled(boolean enabled) {
        return new AgentProfileMutationResponse(true, "状态已更新", null, enabled);
    }

    public static AgentProfileMutationResponse deleted() {
        return new AgentProfileMutationResponse(true, "Agent 已删除", null, null);
    }
}
