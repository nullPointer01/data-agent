package com.ai.datasource.dto;

import com.ai.model.DataSourceConfig;

/**
 * 数据源列表项响应。
 *
 * @author data-agent
 */
public record DataSourceResponse(
        String datasourceId,
        String name,
        String type,
        String host,
        Integer port,
        String dbName,
        String username,
        String passwordMasked,
        String description,
        boolean enabled) {

    private static final String PASSWORD_MASK = "****";

    public static DataSourceResponse from(DataSourceConfig config) {
        return new DataSourceResponse(
                config.getDatasourceId(),
                config.getName(),
                config.getType(),
                defaultString(config.getHost()),
                config.getPort() == null ? 0 : config.getPort(),
                defaultString(config.getDbName()),
                defaultString(config.getUsername()),
                config.getPassword() == null ? "" : PASSWORD_MASK,
                defaultString(config.getDescription()),
                config.isEnabled());
    }

    private static String defaultString(String value) {
        return value == null ? "" : value;
    }
}
