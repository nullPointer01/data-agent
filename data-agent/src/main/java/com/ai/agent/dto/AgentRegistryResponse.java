package com.ai.agent.dto;

import java.util.List;

/**
 * Agent 注册中心快照。
 *
 * @param success 是否成功
 * @param systemSpecialistCount 系统专家数量
 * @param tenantAgentCount 租户 Agent 数量
 * @param enabledTenantAgentCount 已启用租户 Agent 数量
 * @param capabilities 能力分组
 * @param entries 注册条目
 * @author data-agent
 */
public record AgentRegistryResponse(boolean success,
        int systemSpecialistCount,
        int tenantAgentCount,
        int enabledTenantAgentCount,
        List<AgentRegistryCapabilityResponse> capabilities,
        List<AgentRegistryEntryResponse> entries) {
}
