package com.ai.repository;

import com.ai.model.SkillConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SkillConfigRepository extends JpaRepository<SkillConfig, String> {
    List<SkillConfig> findByTenantId(String tenantId);

    List<SkillConfig> findByTenantIdAndEnabledTrue(String tenantId);

    Optional<SkillConfig> findBySkillIdAndTenantId(String skillId, String tenantId);

    List<SkillConfig> findByTenantIdAndNameContainingIgnoreCase(String tenantId, String name);

    long countByIsDefaultTrue();

    List<SkillConfig> findByEnabledTrue();
}
