package com.ai.repository;

import com.ai.model.ModelConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 租户隔离的模型配置仓储。
 *
 * @author data-agent
 */
public interface ModelConfigRepository extends JpaRepository<ModelConfig, String> {

    /**
     * 查询租户的模型配置。
     *
     * @param tenantId 租户 ID
     * @return 租户模型配置列表
     */
    List<ModelConfig> findByTenantId(String tenantId);

    /**
     * 查询租户已启用的模型配置。
     *
     * @param tenantId 租户 ID
     * @return 已启用租户模型配置列表
     */
    List<ModelConfig> findByTenantIdAndEnabledTrue(String tenantId);

    /**
     * 查询租户已启用且为默认的模型配置。
     *
     * @param tenantId 租户 ID
     * @return 已启用且为默认的租户模型配置列表
     */
    List<ModelConfig> findByTenantIdAndEnabledTrueAndIsDefaultTrue(String tenantId);

    /**
     * 查询租户内的一个模型配置。
     *
     * @param modelId 模型 ID
     * @param tenantId 租户 ID
     * @return 匹配的模型配置
     */
    Optional<ModelConfig> findByModelIdAndTenantId(String modelId, String tenantId);

    /**
     * 统计默认模型配置的数量。
     *
     * @return 默认模型数量
     */
    long countByIsDefaultTrue();

    /**
     * 清除一个租户内除指定模型外其他模型的默认标记。
     *
     * @param tenantId 租户 ID
     * @param excludedModelId 保持默认的模型 ID
     * @return 受影响的行数
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
     * 查询所有已启用的模型配置。
     *
     * @return 已启用的模型配置列表
     */
    List<ModelConfig> findByEnabledTrue();
}
