package com.ai.service.connector;

import com.ai.datasource.dto.DataSourceListResponse;
import com.ai.datasource.dto.DataSourceMutationResponse;
import com.ai.datasource.dto.DataSourceRequest;
import com.ai.datasource.dto.DataSourceResponse;
import com.ai.model.DataSourceConfig;
import com.ai.repository.DataSourceConfigRepository;
import com.ai.security.SecurityContextHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 数据源管理应用服务。
 *
 * @author data-agent
 */
@Service
public class DataSourceService {

    private static final int UNKNOWN_PORT = 0;
    private static final String EMPTY_DATASOURCE_MESSAGE = "当前没有配置数据源，请在「数据源」面板添加";

    private final DataSourceConfigRepository repository;

    private final SecurityContextHelper securityContextHelper;

    public DataSourceService(DataSourceConfigRepository repository, SecurityContextHelper securityContextHelper) {
        this.repository = repository;
        this.securityContextHelper = securityContextHelper;
    }

    public DataSourceListResponse list() {
        String tenantId = securityContextHelper.getCurrentTenantId();
        List<DataSourceResponse> datasources = repository.findByTenantId(tenantId).stream()
                .map(DataSourceResponse::from)
                .collect(Collectors.toList());
        return new DataSourceListResponse(true, datasources);
    }

    public String listEnabledForAgent() {
        String tenantId = securityContextHelper.getCurrentTenantId();
        List<DataSourceConfig> configs = repository.findByTenantIdAndEnabledTrue(tenantId);
        if (configs.isEmpty()) {
            return EMPTY_DATASOURCE_MESSAGE;
        }
        return configs.stream()
                .map(this::formatForAgent)
                .collect(Collectors.joining("\n"));
    }

    @Transactional(rollbackFor = Exception.class)
    public DataSourceMutationResponse create(DataSourceRequest request) {
        DataSourceConfig config = new DataSourceConfig();
        applyRequest(config, request, true);
        config.setTenantId(securityContextHelper.getCurrentTenantId());
        config.setUserId(securityContextHelper.getCurrentUserId());
        repository.save(config);
        return DataSourceMutationResponse.created(config.getDatasourceId());
    }

    @Transactional(rollbackFor = Exception.class)
    public DataSourceMutationResponse update(String datasourceId, DataSourceRequest request) {
        Optional<DataSourceConfig> optionalConfig = findCurrentTenantDatasource(datasourceId);
        if (optionalConfig.isEmpty()) {
            return DataSourceMutationResponse.failure("数据源不存在");
        }
        DataSourceConfig config = optionalConfig.get();
        applyRequest(config, request, false);
        repository.save(config);
        return DataSourceMutationResponse.success("数据源已更新");
    }

    @Transactional(rollbackFor = Exception.class)
    public DataSourceMutationResponse delete(String datasourceId) {
        Optional<DataSourceConfig> optionalConfig = findCurrentTenantDatasource(datasourceId);
        if (optionalConfig.isEmpty()) {
            return DataSourceMutationResponse.failure("数据源不存在");
        }
        repository.delete(optionalConfig.get());
        return DataSourceMutationResponse.success("数据源已删除");
    }

    @Transactional(rollbackFor = Exception.class)
    public DataSourceMutationResponse toggle(String datasourceId) {
        Optional<DataSourceConfig> optionalConfig = findCurrentTenantDatasource(datasourceId);
        if (optionalConfig.isEmpty()) {
            return DataSourceMutationResponse.failure("数据源不存在");
        }
        DataSourceConfig config = optionalConfig.get();
        config.setEnabled(!config.isEnabled());
        repository.save(config);
        return DataSourceMutationResponse.toggled(config.isEnabled());
    }

    private Optional<DataSourceConfig> findCurrentTenantDatasource(String datasourceId) {
        String tenantId = securityContextHelper.getCurrentTenantId();
        return repository.findByDatasourceIdAndTenantId(datasourceId, tenantId);
    }

    private void applyRequest(DataSourceConfig config, DataSourceRequest request, boolean create) {
        config.setName(request.name());
        config.setType(request.type());
        config.setHost(request.host());
        config.setPort(request.port());
        config.setDbName(request.dbName());
        config.setUsername(request.username());
        if (create || hasText(request.password())) {
            config.setPassword(request.password());
        }
        config.setDescription(request.description());
        if (request.enabled() != null) {
            config.setEnabled(request.enabled());
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String formatForAgent(DataSourceConfig config) {
        return String.format("- %s [%s]: %s:%d/%s",
                config.getName(),
                config.getType(),
                config.getHost(),
                config.getPort() != null ? config.getPort() : UNKNOWN_PORT,
                config.getDbName());
    }
}
