package com.ai.service.connector;

import com.ai.model.DataSourceConfig;
import com.ai.repository.DataSourceConfigRepository;
import com.ai.security.SecurityContextHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Resolves tenant-scoped data source configurations.
 *
 * @author data-agent
 */
@Component
public class DataConnectorLookupService {

    private final DataSourceConfigRepository dataSourceConfigRepository;
    private final SecurityContextHelper securityContextHelper;

    public DataConnectorLookupService(DataSourceConfigRepository dataSourceConfigRepository,
            SecurityContextHelper securityContextHelper) {
        this.dataSourceConfigRepository = dataSourceConfigRepository;
        this.securityContextHelper = securityContextHelper;
    }

    /**
     * Finds an enabled data source in the current tenant scope.
     *
     * @param datasourceIdOrName datasource id or name
     * @return enabled data source configuration
     */
    public DataSourceConfig requireEnabledDatasource(String datasourceIdOrName) {
        if (!StringUtils.hasText(datasourceIdOrName)) {
            throw new IllegalArgumentException("数据源名称不能为空");
        }
        String tenantId = securityContextHelper.getCurrentTenantId();
        return dataSourceConfigRepository.findByTenantIdAndEnabledTrue(tenantId).stream()
                .filter(ds -> datasourceIdOrName.equals(ds.getDatasourceId())
                        || datasourceIdOrName.equals(ds.getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("数据源不存在或未启用: " + datasourceIdOrName));
    }
}
