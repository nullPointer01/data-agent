package com.ai.agent.dto;

/**
 * Agent 注册中心能力分组。
 *
 * @param type Agent 类型
 * @param displayName 展示名称
 * @param capability 能力标识
 * @param description 能力说明
 * @param systemAvailable 系统专家实现是否可用
 * @param tenantAgentCount 租户配置数量
 * @param enabledTenantAgentCount 已启用租户配置数量
 * @author data-agent
 */
public record AgentRegistryCapabilityResponse(String type,
        String displayName,
        String capability,
        String description,
        boolean systemAvailable,
        long tenantAgentCount,
        long enabledTenantAgentCount) {
}
