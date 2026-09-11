package com.ai.agent.capability;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Agent Profile 显式保存的统一能力身份集合。
 *
 * <p>该对象只负责稳定身份的结构校验与顺序去重，不判断当前用户是否有权使用能力。
 * 权限、租户、生命周期与运行时可用性由 {@link AgentCapabilityService} 处理。</p>
 *
 * @author data-agent
 */
public final class AgentCapabilityBindingSet {

    public static final int MAX_BINDINGS = 64;

    private final List<String> identities;

    private AgentCapabilityBindingSet(List<String> identities) {
        this.identities = List.copyOf(identities);
    }

    /**
     * 创建经过格式校验的能力绑定集合。
     *
     * @param values 稳定能力身份列表，不能为 null
     * @return 不可变绑定集合
     */
    public static AgentCapabilityBindingSet of(List<String> values) {
        if (values == null) {
            throw new IllegalArgumentException("能力绑定列表不能为空");
        }
        if (values.size() > MAX_BINDINGS) {
            throw new IllegalArgumentException("Agent 最多绑定 " + MAX_BINDINGS + " 个能力");
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            String identity = normalize(value);
            if (!normalized.add(identity)) {
                throw new IllegalArgumentException("Agent 能力绑定不能重复: " + identity);
            }
        }
        return new AgentCapabilityBindingSet(new ArrayList<>(normalized));
    }

    /**
     * 返回全部稳定身份，保持用户保存顺序。
     *
     * @return 不可变身份列表
     */
    public List<String> identities() {
        return identities;
    }

    /**
     * 返回指定类型对应的原始能力编号。
     *
     * @param type 能力类型
     * @return 去掉类型前缀的稳定编号
     */
    public List<String> sourceIds(AgentCapabilityType type) {
        if (type == null) {
            return List.of();
        }
        return identities.stream()
                .filter(type::owns)
                .map(type::sourceId)
                .toList();
    }

    /**
     * 判断是否绑定指定能力。
     *
     * @param identity 稳定能力身份
     * @return 已绑定时返回 true
     */
    public boolean contains(String identity) {
        return identity != null && identities.contains(identity.trim());
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("能力绑定身份不能为空");
        }
        String identity = value.trim();
        for (AgentCapabilityType type : AgentCapabilityType.values()) {
            if (type.owns(identity)) {
                return type.identity(type.sourceId(identity));
            }
        }
        throw new IllegalArgumentException("不支持的能力绑定身份: " + identity);
    }
}
