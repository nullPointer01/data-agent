package com.ai.repository;

import com.ai.model.SkillConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for tenant-scoped skill configuration.
 *
 * @author data-agent
 */
public interface SkillConfigRepository extends JpaRepository<SkillConfig, String> {

    /**
     * Finds all skills owned by one tenant.
     *
     * @param tenantId tenant id
     * @return skill configs
     */
    List<SkillConfig> findByTenantId(String tenantId);

    /**
     * Finds enabled skills owned by one tenant.
     *
     * @param tenantId tenant id
     * @return enabled skill configs
     */
    List<SkillConfig> findByTenantIdAndEnabledTrue(String tenantId);

    /**
     * Finds one skill by business id and tenant id.
     *
     * @param skillId skill id
     * @param tenantId tenant id
     * @return matched skill config
     */
    Optional<SkillConfig> findBySkillIdAndTenantId(String skillId, String tenantId);

    /**
     * Searches tenant skills by name.
     *
     * @param tenantId tenant id
     * @param name skill name keyword
     * @return matched skill configs
     */
    List<SkillConfig> findByTenantIdAndNameContainingIgnoreCase(String tenantId, String name);

    /**
     * Counts default skills.
     *
     * @return default skill count
     */
    long countByIsDefaultTrue();

    /**
     * Finds all enabled skills.
     *
     * @return enabled skill configs
     */
    List<SkillConfig> findByEnabledTrue();
}
