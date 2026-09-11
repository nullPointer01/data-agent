package com.ai.agent.tool;

import com.ai.agent.capability.AgentCapabilityBindingSnapshot;
import com.ai.agent.capability.AgentCapabilityScope;
import com.ai.skill.Skill;
import com.ai.skill.SkillManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

/**
 * 暴露给 Agent 运行时使用的技能工具服务。
 *
 * @author data-agent
 */
@Service
public class AgentSkillToolService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentSkillToolService.class);
    private static final String EMPTY_SKILL_MESSAGE = "当前没有可用的技能";
    private static final String SKILL_UNAVAILABLE_MESSAGE = "请求的 Skill 未绑定、已停用或未加载";

    private final SkillManager skillManager;

    public AgentSkillToolService(SkillManager skillManager) {
        this.skillManager = skillManager;
    }

    /**
     * 列出当前运行时已注册的技能。
     *
     * @return 模型可读的技能列表
     */
    public String listAvailableSkills() {
        AgentCapabilityBindingSnapshot snapshot = requireCapabilitySnapshot();
        String catalog = snapshot.skillIds().stream()
                .map(skillId -> describeSkill(skillId, skillManager.findSkillById(skillId)))
                .filter(description -> description != null)
                .collect(Collectors.joining("\n"));
        if (catalog.isBlank()) {
            return EMPTY_SKILL_MESSAGE;
        }
        return catalog;
    }

    /**
     * 使用指定技能处理用户查询。
     *
     * @param skillId 模型选择的 Skill 稳定编号
     * @param query 用户分析问题
     * @return 技能执行结果或可恢复错误提示
     */
    public String useSkill(String skillId, String query) {
        String normalizedSkillId = normalizeSkillId(skillId);
        AgentCapabilityBindingSnapshot snapshot = requireCapabilitySnapshot();
        if (!snapshot.allowsSkill(normalizedSkillId)) {
            throw new SecurityException("当前 Agent 未绑定请求的 Skill");
        }
        LOGGER.info("Agent 调用绑定 Skill: skillIdLength={}, queryLength={}",
                normalizedSkillId.length(), lengthOf(query));
        String result = skillManager.processWithSkillById(normalizedSkillId, query, null);
        if (result == null) {
            throw new IllegalStateException(SKILL_UNAVAILABLE_MESSAGE);
        }
        return result;
    }

    private AgentCapabilityBindingSnapshot requireCapabilitySnapshot() {
        return AgentCapabilityScope.current()
                .orElseThrow(() -> new SecurityException("当前执行没有 Agent 能力作用域"));
    }

    private String normalizeSkillId(String skillId) {
        if (skillId == null || skillId.isBlank()) {
            throw new IllegalArgumentException("Skill ID 不能为空");
        }
        String normalized = skillId.trim();
        normalized = normalized.startsWith("skill:")
                ? normalized.substring("skill:".length())
                : normalized;
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Skill ID 不能为空");
        }
        return normalized;
    }

    private String describeSkill(String skillId, Skill skill) {
        if (skill == null) {
            return null;
        }
        return "- " + skillId + " | " + skill.getName() + ": " + skill.getDescription();
    }

    private int lengthOf(String value) {
        return value == null ? 0 : value.length();
    }
}
