package com.ai.datasource.dto;

import java.util.List;

/**
 * Data source list response.
 *
 * @author data-agent
 */
public record DataSourceListResponse(boolean success, List<DataSourceResponse> datasources) {
}
