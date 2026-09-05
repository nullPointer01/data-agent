package com.ai.skill;

import com.ai.mcp.McpContextManager;
import com.ai.mcp.McpModelService;
import com.ai.service.VectorMemoryService;
import com.ai.vector.VectorDocumentTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 运行时技能注册表，负责技能注册、检索和上下文执行。
 *
 * @author data-agent
 */
@Component
public class SkillManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(SkillManager.class);
    private static final String DEFAULT_MODEL_ID = "default";
    private static final String VECTOR_EMPTY_CONTENT = "";
    private static final String COMMAND_PREFIX = "/";
    private static final String COMMAND_SPLIT_DELIMITER = " ";
    private static final int COMMAND_SPLIT_LIMIT = 2;
    private static final int COMMAND_SKILL_NAME_START_INDEX = 1;
    private static final String SKILL_NAME_REQUIRED_MESSAGE = "技能名称不能为空";

    private final List<Skill> skills = new ArrayList<>();
    private final Map<String, Skill> skillByName = new ConcurrentHashMap<>();
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private volatile Skill defaultSkill;
    private final McpContextManager mcpContextManager;
    private final McpModelService mcpModelService;
    private final VectorMemoryService vectorMemoryService;
    private final SkillMatcher skillMatcher;

    public SkillManager(McpContextManager mcpContextManager, McpModelService mcpModelService,
            VectorMemoryService vectorMemoryService, SkillMatcher skillMatcher) {
        this.mcpContextManager = mcpContextManager;
        this.mcpModelService = mcpModelService;
        this.vectorMemoryService = vectorMemoryService;
        this.skillMatcher = skillMatcher;
    }

    public void registerSkill(Skill skill) {
        registerSkill(skill, true);
    }

    public void registerSkillWithoutVectorRefresh(Skill skill) {
        registerSkill(skill, false);
    }

    private void registerSkill(Skill skill, boolean refreshVectorIndex) {
        upsertSkill(skill);
        if (refreshVectorIndex) {
            refreshSkillVectorIndex(skill, false);
        }
        LOGGER.info("技能已注册: {}", skill.getName());
    }

    public void registerDefaultSkill(Skill skill) {
        registerDefaultSkill(skill, true);
    }

    public void registerDefaultSkillWithoutVectorRefresh(Skill skill) {
        registerDefaultSkill(skill, false);
    }

    private void registerDefaultSkill(Skill skill, boolean refreshVectorIndex) {
        upsertSkill(skill);
        this.defaultSkill = skill;
        if (refreshVectorIndex) {
            refreshSkillVectorIndex(skill, true);
        }
        LOGGER.info("默认技能已注册: {}", skill.getName());
    }

    public void unregisterSkill(String name) {
        if (!hasText(name)) {
            return;
        }
        String normalizedName = normalizeName(name);
        rwLock.writeLock().lock();
        try {
            skills.removeIf(skill -> normalizedName.equals(normalizeName(skill.getName())));
            skillByName.remove(normalizedName);
            if (defaultSkill != null && normalizedName.equals(normalizeName(defaultSkill.getName()))) {
                defaultSkill = null;
            }
        } finally {
            rwLock.writeLock().unlock();
        }
        try {
            vectorMemoryService.removeFromStore(VectorDocumentTypes.SKILL, name);
        } catch (Exception e) {
            LOGGER.warn("技能向量索引删除失败: {}", name, e);
        }
    }

    public Skill getDefaultSkill() {
        return defaultSkill;
    }

    public List<Skill> getAllSkills() {
        rwLock.readLock().lock();
        try {
            return List.copyOf(skills);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public Skill findSkill(String query) {
        SkillMatchResult result = findSkillWithScore(query);
        return result != null ? result.skill : null;
    }

    public SkillMatchResult findSkillWithScore(String query) {
        if (!hasText(query)) {
            return null;
        }
        return skillMatcher.findBestMatch(query, getAllSkills());
    }

    public Skill findSkillByName(String name) {
        if (!hasText(name)) {
            return null;
        }
        Skill skill = skillByName.get(normalizeName(name));
        if (skill != null) {
            return skill;
        }
        for (Skill item : getAllSkills()) {
            if (normalizeName(item.getName()).equals(normalizeName(name))) {
                return item;
            }
        }
        return null;
    }

    public String processWithSkill(String query, Object data) {
        Skill skill = findSkill(query);
        if (skill == null) {
            skill = defaultSkill;
        }
        return executeResolvedSkill(skill, query, data);
    }

    public String processWithSkillByName(String skillName, String query, Object data) {
        Skill skill = findSkillByName(skillName);
        if (skill == null) {
            skill = defaultSkill;
        }
        return executeResolvedSkill(skill, query, data);
    }

    public String processWithCommand(String command, Object data) {
        if (!hasText(command) || !command.startsWith(COMMAND_PREFIX)) {
            return null;
        }
        String[] parts = command.split(COMMAND_SPLIT_DELIMITER, COMMAND_SPLIT_LIMIT);
        String skillName = parts[0].substring(COMMAND_SKILL_NAME_START_INDEX);
        if (!hasText(skillName)) {
            return null;
        }
        String actualQuery = parts.length > 1 ? parts[1] : "";
        return processWithSkillByName(skillName, actualQuery, data);
    }

    /**
     * 判断斜杠命令在当前注册表中是否有可执行技能或默认兜底。
     *
     * @param command 斜杠命令
     * @return 是否可作为命令执行
     */
    public boolean canProcessCommand(String command) {
        if (!hasText(command) || !command.startsWith(COMMAND_PREFIX)) {
            return false;
        }
        String[] parts = command.split(COMMAND_SPLIT_DELIMITER, COMMAND_SPLIT_LIMIT);
        String skillName = parts[0].substring(COMMAND_SKILL_NAME_START_INDEX);
        return hasText(skillName) && (findSkillByName(skillName) != null || defaultSkill != null);
    }

    private String normalizeName(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    private void upsertSkill(Skill skill) {
        validateSkill(skill);
        String normalizedName = normalizeName(skill.getName());
        rwLock.writeLock().lock();
        try {
            skills.removeIf(item -> normalizedName.equals(normalizeName(item.getName())));
            skills.add(skill);
            skillByName.put(normalizedName, skill);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    private void validateSkill(Skill skill) {
        if (skill == null || !hasText(skill.getName())) {
            throw new IllegalArgumentException(SKILL_NAME_REQUIRED_MESSAGE);
        }
    }

    private void refreshSkillVectorIndex(Skill skill, boolean defaultSkillRegistration) {
        try {
            vectorMemoryService.removeFromStore(VectorDocumentTypes.SKILL, skill.getName());
            vectorMemoryService.indexSkill(skill.getName(), skill.getDescription(), VECTOR_EMPTY_CONTENT);
        } catch (Exception e) {
            if (defaultSkillRegistration) {
                LOGGER.warn("默认技能向量索引刷新失败: {}", skill.getName(), e);
            } else {
                LOGGER.warn("技能向量索引刷新失败: {}", skill.getName(), e);
            }
        }
    }

    private String executeResolvedSkill(Skill skill, String query, Object data) {
        if (skill == null) {
            return null;
        }
        String contextId = mcpContextManager.createContext(skill.getName(), DEFAULT_MODEL_ID);
        try {
            return skill.processWithContext(query, data, contextId, mcpModelService);
        } finally {
            mcpContextManager.destroyContext(contextId);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /**
     * 技能匹配结果和命中分数。
     */
    public static class SkillMatchResult {
        public final Skill skill;
        public final double score;

        public SkillMatchResult(Skill skill, double score) {
            this.skill = skill;
            this.score = score;
        }
    }
}
