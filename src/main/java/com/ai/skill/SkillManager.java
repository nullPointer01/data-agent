package com.ai.skill;

import com.ai.mcp.McpContextManager;
import com.ai.mcp.McpModelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 运行时 Skill 注册表，仅支持按持久化稳定 ID 精确查找和执行。
 *
 * <p>Skill 的可见性和授权由 Agent 能力快照负责；本注册表不进行名称、关键词、
 * 向量匹配或默认 Skill 回退。</p>
 *
 * @author data-agent
 */
@Component
public class SkillManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(SkillManager.class);
    private final Map<String, Skill> skillsByStableId = new ConcurrentHashMap<>();
    private final McpContextManager mcpContextManager;
    private final McpModelService mcpModelService;

    public SkillManager(McpContextManager mcpContextManager, McpModelService mcpModelService) {
        this.mcpContextManager = mcpContextManager;
        this.mcpModelService = mcpModelService;
    }

    /**
     * 注册或替换一个由稳定 ID 标识的运行时 Skill。
     *
     * @param stableId Skill 配置的持久化稳定 ID
     * @param skill 运行时 Skill
     */
    public void registerSkill(String stableId, Skill skill) {
        String normalizedId = requireStableId(stableId);
        validateSkill(skill);
        skillsByStableId.put(normalizedId, skill);
        LOGGER.info("Skill 已注册: stableId={}, name={}", normalizedId, skill.getName());
    }

    /**
     * 启动加载入口。Skill 已不维护向量索引，因此与普通注册语义一致。
     *
     * @param stableId Skill 配置的持久化稳定 ID
     * @param skill 运行时 Skill
     */
    public void registerSkillWithoutVectorRefresh(String stableId, Skill skill) {
        registerSkill(stableId, skill);
    }

    /**
     * 按稳定 ID 注销 Skill。名称仅用于管理日志，不参与查找或回退。
     *
     * @param stableId Skill 配置的持久化稳定 ID
     * @param name Skill 显示名称
     */
    public void unregisterSkill(String stableId, String name) {
        if (stableId == null || stableId.isBlank()) {
            return;
        }
        String normalizedId = stableId.trim();
        Skill removed = skillsByStableId.remove(normalizedId);
        if (removed != null) {
            LOGGER.info("Skill 已注销: stableId={}, name={}", normalizedId, name);
        }
    }

    /**
     * 按持久化稳定 ID 精确查找 Skill。
     *
     * @param stableId Skill 配置的持久化稳定 ID
     * @return 精确匹配的运行时 Skill，不存在时为 null
     */
    public Skill findSkillById(String stableId) {
        if (stableId == null || stableId.isBlank()) {
            return null;
        }
        return skillsByStableId.get(stableId.trim());
    }

    /**
     * 按持久化稳定 ID 精确执行 Skill，不允许名称匹配或默认回退。
     *
     * @param stableId Skill 配置的持久化稳定 ID
     * @param query 用户任务
     * @param data 可选数据
     * @return Skill 结果，不存在时返回 null
     */
    public String processWithSkillById(String stableId, String query, Object data) {
        Skill skill = findSkillById(stableId);
        if (skill == null) {
            return null;
        }
        String contextId = mcpContextManager.createContext();
        try {
            return skill.processWithContext(query, data, contextId, mcpModelService);
        } finally {
            mcpContextManager.destroyContext(contextId);
        }
    }

    private String requireStableId(String stableId) {
        if (stableId == null || stableId.isBlank()) {
            throw new IllegalArgumentException("Skill 稳定 ID 不能为空");
        }
        return stableId.trim();
    }

    private void validateSkill(Skill skill) {
        if (skill == null || skill.getName() == null || skill.getName().isBlank()) {
            throw new IllegalArgumentException("Skill 名称不能为空");
        }
    }
}
