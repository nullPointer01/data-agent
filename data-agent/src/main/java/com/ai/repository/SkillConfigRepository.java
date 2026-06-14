package com.ai.repository;

import com.ai.model.SkillConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 租户隔离的技能配置仓储。
 *
 * @author data-agent
 */
public interface SkillConfigRepository extends JpaRepository<SkillConfig, String> {

    /**
     * 查询一个租户拥有的所有技能。
     *
     * @param tenantId 租户 ID
     * @return 技能配置列表
     */
    List<SkillConfig> findByTenantId(String tenantId);

    /**
     * 查询一个租户拥有的已启用技能。
     *
     * @param tenantId 租户 ID
     * @return 已启用技能配置列表
     */
    List<SkillConfig> findByTenantIdAndEnabledTrue(String tenantId);

    /**
     * 按业务 ID 和租户 ID 查询一个技能。
     *
     * @param skillId 技能 ID
     * @param tenantId 租户 ID
     * @return 匹配的技能配置
     */
    Optional<SkillConfig> findBySkillIdAndTenantId(String skillId, String tenantId);

    /**
     * 按名称搜索租户技能。
     *
     * @param tenantId 租户 ID
     * @param name 技能名称关键字
     * @return 匹配的技能配置列表
     */
    List<SkillConfig> findByTenantIdAndNameContainingIgnoreCase(String tenantId, String name);

    /**
     * 统计默认技能数量。
     *
     * @return 默认技能数量
     */
    long countByIsDefaultTrue();

    /**
     * 查询所有已启用的技能。
     *
     * @return 已启用技能配置列表
     */
    List<SkillConfig> findByEnabledTrue();
}
