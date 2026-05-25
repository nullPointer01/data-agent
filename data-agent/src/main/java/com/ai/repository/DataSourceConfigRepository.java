package com.ai.repository;

import com.ai.model.DataSourceConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for data source configuration.
 *
 * @author data-agent
 */
public interface DataSourceConfigRepository extends JpaRepository<DataSourceConfig, String> {

    /**
     * Find enabled tenant data sources.
     *
     * @param tenantId tenant id
     * @return enabled data sources
     */
    List<DataSourceConfig> findByTenantIdAndEnabledTrue(String tenantId);

    /**
     * Find tenant data sources.
     *
     * @param tenantId tenant id
     * @return data sources
     */
    List<DataSourceConfig> findByTenantId(String tenantId);

    /**
     * Find a data source by id and tenant id.
     *
     * @param datasourceId data source id
     * @param tenantId tenant id
     * @return data source config
     */
    Optional<DataSourceConfig> findByDatasourceIdAndTenantId(String datasourceId, String tenantId);
}
