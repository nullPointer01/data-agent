package com.ai.repository;

import com.ai.model.ModelConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Repository for tenant-scoped model configuration.
 *
 * @author data-agent
 */
public interface ModelConfigRepository extends JpaRepository<ModelConfig, String> {

    /**
     * Finds model configurations for a tenant.
     *
     * @param tenantId tenant id
     * @return tenant model configurations
     */
    List<ModelConfig> findByTenantId(String tenantId);

    /**
     * Finds enabled model configurations for a tenant.
     *
     * @param tenantId tenant id
     * @return enabled tenant model configurations
     */
    List<ModelConfig> findByTenantIdAndEnabledTrue(String tenantId);

    /**
     * Finds default enabled model configurations for a tenant.
     *
     * @param tenantId tenant id
     * @return default enabled tenant model configurations
     */
    List<ModelConfig> findByTenantIdAndEnabledTrueAndIsDefaultTrue(String tenantId);

    /**
     * Finds one model configuration within a tenant.
     *
     * @param modelId model id
     * @param tenantId tenant id
     * @return matched model configuration
     */
    Optional<ModelConfig> findByModelIdAndTenantId(String modelId, String tenantId);

    /**
     * Counts default model configurations.
     *
     * @return default model count
     */
    long countByIsDefaultTrue();

    /**
     * Clears the default flag for models in one tenant except the provided model.
     *
     * @param tenantId tenant id
     * @param excludedModelId model id to keep default
     * @return affected rows
     */
    @Modifying
    @Query("""
            UPDATE ModelConfig m
            SET m.isDefault = false
            WHERE m.tenantId = :tenantId
              AND m.modelId <> :excludedModelId
              AND m.isDefault = true
            """)
    int clearOtherDefaults(@Param("tenantId") String tenantId, @Param("excludedModelId") String excludedModelId);

    /**
     * Finds all enabled model configurations.
     *
     * @return enabled model configurations
     */
    List<ModelConfig> findByEnabledTrue();
}
