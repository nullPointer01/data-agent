package com.ai.agent.capability;

import com.ai.agent.runtime.AgentExecutionMode;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 一层 Agent 执行开始时解析出的不可变能力边界。
 *
 * @param agentId 当前执行 Agent 编号
 * @param mode 已解析的执行模式
 * @param requestedBindings Profile 请求的稳定能力身份
 * @param toolNames 当前允许暴露的 Tool 名称
 * @param skillIds 当前允许调用的 Skill 稳定编号
 * @param subAgentIds 当前允许委派的子 Agent 编号
 * @param exclusions 被权限、生命周期、运行策略或模式排除的安全原因
 * @author data-agent
 */
public record AgentCapabilityBindingSnapshot(
        String agentId,
        AgentExecutionMode mode,
        List<String> requestedBindings,
        Set<String> toolNames,
        Set<String> skillIds,
        Set<String> subAgentIds,
        List<CapabilityExclusion> exclusions) {

    public AgentCapabilityBindingSnapshot {
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("能力快照 Agent ID 不能为空");
        }
        if (mode == null) {
            throw new IllegalArgumentException("能力快照执行模式不能为空");
        }
        requestedBindings = requestedBindings == null ? List.of() : List.copyOf(requestedBindings);
        toolNames = immutableSet(toolNames);
        skillIds = immutableSet(skillIds);
        subAgentIds = immutableSet(subAgentIds);
        exclusions = exclusions == null ? List.of() : List.copyOf(exclusions);
    }

    /**
     * 判断当前 Agent 是否允许调用指定 Tool。
     *
     * @param toolName Tool 名称
     * @return 精确绑定且运行时可用时返回 true
     */
    public boolean allowsTool(String toolName) {
        return toolName != null && toolNames.contains(toolName);
    }

    /**
     * 判断当前 Agent 是否允许调用指定 Skill。
     *
     * @param skillId Skill 稳定编号
     * @return 精确绑定且运行时可用时返回 true
     */
    public boolean allowsSkill(String skillId) {
        return skillId != null && skillIds.contains(skillId);
    }

    /**
     * 判断当前 Agent 是否允许委派指定子 Agent。
     *
     * @param subAgentId 子 Agent 编号
     * @return 精确绑定且运行时可用时返回 true
     */
    public boolean allowsSubAgent(String subAgentId) {
        return subAgentId != null && subAgentIds.contains(subAgentId);
    }

    private static Set<String> immutableSet(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }

    /**
     * 一个请求绑定未进入本次有效能力集合的安全说明。
     *
     * @param identity 能力稳定身份
     * @param reasonCode 稳定原因码
     * @param summary 不包含凭据和内部异常的说明
     */
    public record CapabilityExclusion(String identity, String reasonCode, String summary) {

        public CapabilityExclusion {
            identity = identity == null ? "unknown" : identity;
            reasonCode = reasonCode == null || reasonCode.isBlank() ? "CAPABILITY_EXCLUDED" : reasonCode;
            summary = summary == null || summary.isBlank() ? "能力当前不可用" : summary;
        }
    }
}
