package com.ai.service.connector;

import com.ai.model.DataSourceConfig;
import com.ai.repository.DataSourceConfigRepository;
import com.ai.security.SecurityContextHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 解析租户范围的数据源配置。
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
     * 在当前租户范围内查找已启用的数据源。
     *
     * @param datasourceIdOrName 数据源 ID 或名称
     * @return 已启用的数据源配置
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
