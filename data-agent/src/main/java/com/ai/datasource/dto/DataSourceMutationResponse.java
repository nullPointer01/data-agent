package com.ai.datasource.dto;

/**
 * Response for data source mutation commands.
 *
 * @author data-agent
 */
public record DataSourceMutationResponse(
        boolean success,
        String message,
        String datasourceId,
        Boolean enabled) {

    public static DataSourceMutationResponse created(String datasourceId) {
        return new DataSourceMutationResponse(true, "数据源已创建", datasourceId, null);
    }

    public static DataSourceMutationResponse success(String message) {
        return new DataSourceMutationResponse(true, message, null, null);
    }

    public static DataSourceMutationResponse toggled(boolean enabled) {
        return new DataSourceMutationResponse(true, "状态已更新", null, enabled);
    }

    public static DataSourceMutationResponse failure(String message) {
        return new DataSourceMutationResponse(false, message, null, null);
    }
}
