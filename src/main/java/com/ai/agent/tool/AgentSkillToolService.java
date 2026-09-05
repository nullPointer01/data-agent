package com.ai.agent.tool;

import com.ai.skill.Skill;
import com.ai.skill.SkillManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
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
    private static final String SKILL_NOT_FOUND_PREFIX = "技能 '";
    private static final String SKILL_NOT_FOUND_SUFFIX = "' 不存在或执行失败，可用技能: ";

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
        List<Skill> skills = skillManager.getAllSkills();
        if (skills.isEmpty()) {
            return EMPTY_SKILL_MESSAGE;
        }
        return skills.stream()
                .map(skill -> "- " + skill.getName() + ": " + skill.getDescription())
                .collect(Collectors.joining("\n"));
    }

    /**
     * 使用指定技能处理用户查询。
     *
     * @param skillName 模型选择的技能名称
     * @param query 用户分析问题
     * @return 技能执行结果或可恢复错误提示
     */
    public String useSkill(String skillName, String query) {
        LOGGER.info("Agent 调用技能: skillNameLength={}, queryLength={}", lengthOf(skillName), lengthOf(query));
        String result = skillManager.processWithSkillByName(skillName, query, null);
        if (result == null) {
            return SKILL_NOT_FOUND_PREFIX + skillName + SKILL_NOT_FOUND_SUFFIX + listAvailableSkills();
        }
        return result;
    }

    private int lengthOf(String value) {
        return value == null ? 0 : value.length();
    }
}
