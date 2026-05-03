package com.ai.repository;

import com.ai.model.ModelConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ModelConfigRepository extends JpaRepository<ModelConfig, String> {
    List<ModelConfig> findByTenantId(String tenantId);

    List<ModelConfig> findByTenantIdAndEnabledTrue(String tenantId);

    Optional<ModelConfig> findByModelIdAndTenantId(String modelId, String tenantId);

    long countByIsDefaultTrue();

    List<ModelConfig> findByEnabledTrue();
}
