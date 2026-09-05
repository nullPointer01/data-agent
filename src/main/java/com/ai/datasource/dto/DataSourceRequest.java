package com.ai.datasource.dto;

/**
 * 创建或更新数据源的请求。
 *
 * @author data-agent
 */
public record DataSourceRequest(
        String name,
        String type,
        String host,
        Integer port,
        String dbName,
        String username,
        String password,
        String description,
        Boolean enabled) {
}
