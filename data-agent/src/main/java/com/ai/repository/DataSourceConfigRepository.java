package com.ai.repository;

import com.ai.model.DataSourceConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 数据源配置仓储。
 *
 * @author data-agent
 */
public interface DataSourceConfigRepository extends JpaRepository<DataSourceConfig, String> {

    /**
     * 查询租户已启用的数据源。
     *
     * @param tenantId 租户 ID
     * @return 已启用数据源列表
     */
    List<DataSourceConfig> findByTenantIdAndEnabledTrue(String tenantId);

    /**
     * 查询租户的数据源。
     *
     * @param tenantId 租户 ID
     * @return 数据源列表
     */
    List<DataSourceConfig> findByTenantId(String tenantId);

    /**
     * 按数据源 ID 和租户 ID 查询数据源。
     *
     * @param datasourceId 数据源 ID
     * @param tenantId 租户 ID
     * @return 数据源配置
     */
    Optional<DataSourceConfig> findByDatasourceIdAndTenantId(String datasourceId, String tenantId);
}
