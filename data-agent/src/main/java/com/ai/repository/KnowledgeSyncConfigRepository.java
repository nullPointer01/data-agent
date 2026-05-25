package com.ai.repository;

import com.ai.model.KnowledgeSyncConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for knowledge synchronization configuration.
 *
 * @author data-agent
 */
public interface KnowledgeSyncConfigRepository extends JpaRepository<KnowledgeSyncConfig, Long> {

    /**
     * Find enabled automatic sync configs.
     *
     * @return enabled config list
     */
    List<KnowledgeSyncConfig> findByEnabledTrue();

    /**
     * Find sync config by knowledge id and tenant id.
     *
     * @param knowledgeId knowledge id
     * @param tenantId tenant id
     * @return sync config
     */
    Optional<KnowledgeSyncConfig> findByKnowledgeIdAndTenantId(String knowledgeId, String tenantId);

    /**
     * Find sync configs by tenant id.
     *
     * @param tenantId tenant id
     * @return tenant sync configs
     */
    List<KnowledgeSyncConfig> findByTenantId(String tenantId);
}
