package com.ai.agent.dto;

import java.util.List;

/**
 * Agent 注册中心条目。
 *
 * @param registryId 注册编号
 * @param name 展示名称
 * @param type Agent 类型
 * @param source 来源，取值 SYSTEM 或 TENANT
 * @param capability 能力标识
 * @param description 能力说明
 * @param enabled 是否启用
 * @param runtimeAvailable 运行时实现是否可用
 * @param modelId 绑定模型编号
 * @param skillId 绑定技能编号
 * @param datasourceId 绑定数据源编号
 * @param tags 标签列表
 * @author data-agent
 */
public record AgentRegistryEntryResponse(String registryId,
        String name,
        String type,
        String source,
        String capability,
        String description,
        boolean enabled,
        boolean runtimeAvailable,
        String modelId,
        String skillId,
        String datasourceId,
        List<String> tags) {
}
