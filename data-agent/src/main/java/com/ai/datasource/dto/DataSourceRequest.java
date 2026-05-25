package com.ai.datasource.dto;

/**
 * Request for creating or updating a data source.
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
