package com.ai.agent.tool;

import com.ai.service.connector.DataConnectorService;
import com.ai.service.connector.DataSourceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Data-source tools for schema discovery, preview and read-only querying.
 *
 * @author data-agent
 */
@Service
public class AgentDataSourceToolService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentDataSourceToolService.class);
    private static final int DEFAULT_SQL_MAX_ROWS = 200;
    private static final int DEFAULT_PREVIEW_ROWS = 50;

    private final DataConnectorService dataConnectorService;
    private final DataSourceService dataSourceService;

    public AgentDataSourceToolService(DataConnectorService dataConnectorService,
            DataSourceService dataSourceService) {
        this.dataConnectorService = dataConnectorService;
        this.dataSourceService = dataSourceService;
    }

    /**
     * Lists tenant-enabled data sources that agents may use.
     *
     * @return model-readable data source list
     */
    public String listDataSources() {
        return dataSourceService.listEnabledForAgent();
    }

    /**
     * Gets database schema or HTTP data shape for one configured data source.
     *
     * @param datasourceName data source name or id
     * @return schema or preview text
     */
    public String getDatabaseSchema(String datasourceName) {
        LOGGER.info("Agent getDatabaseSchema for datasource={}", datasourceName);
        return dataConnectorService.schema(datasourceName);
    }

    /**
     * Executes a tenant-scoped read-only SQL query.
     *
     * @param datasourceName data source name or id
     * @param sql read-only SQL
     * @return query result in text table format
     */
    public String executeSql(String datasourceName, String sql) {
        LOGGER.info("Agent executeSql on datasource={}, sql={}", datasourceName, sql);
        return dataConnectorService.executeReadOnlySql(datasourceName, sql, DEFAULT_SQL_MAX_ROWS);
    }

    /**
     * Previews the first rows or remote response body for one data source.
     *
     * @param datasourceName data source name or id
     * @return preview result
     */
    public String previewDataSource(String datasourceName) {
        LOGGER.info("Agent previewDataSource datasource={}", datasourceName);
        return dataConnectorService.preview(datasourceName, DEFAULT_PREVIEW_ROWS);
    }
}
