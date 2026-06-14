package com.ai.datasource.dto;

import java.util.List;

/**
 * 数据源列表响应。
 *
 * @author data-agent
 */
public record DataSourceListResponse(boolean success, List<DataSourceResponse> datasources) {
}
