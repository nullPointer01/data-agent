package com.ai.service.connector;

import com.ai.model.DataSourceConfig;
import org.springframework.stereotype.Service;

/**
 * Facade for datasource testing, schema discovery and read-only query execution.
 *
 * @author data-agent
 */
@Service
public class DataConnectorService {

    private final DataConnectorLookupService lookupService;
    private final DataConnectorSqlPolicy sqlPolicy;
    private final DataConnectorJdbcService jdbcService;
    private final DataConnectorHttpService httpService;

    public DataConnectorService(DataConnectorLookupService lookupService, DataConnectorSqlPolicy sqlPolicy,
            DataConnectorJdbcService jdbcService, DataConnectorHttpService httpService) {
        this.lookupService = lookupService;
        this.sqlPolicy = sqlPolicy;
        this.jdbcService = jdbcService;
        this.httpService = httpService;
    }

    /**
     * Finds an enabled datasource in the current tenant scope.
     *
     * @param datasourceIdOrName datasource id or name
     * @return datasource configuration
     */
    public DataSourceConfig requireEnabledDatasource(String datasourceIdOrName) {
        return lookupService.requireEnabledDatasource(datasourceIdOrName);
    }

    /**
     * Tests one datasource connection.
     *
     * @param datasourceIdOrName datasource id or name
     * @return textual test result
     */
    public String test(String datasourceIdOrName) {
        DataSourceConfig datasource = requireEnabledDatasource(datasourceIdOrName);
        if (sqlPolicy.isHttpType(datasource.getType())) {
            return httpService.testHttp(datasource);
        }
        return jdbcService.testJdbc(datasource);
    }

    /**
     * Reads a schema-style view for one datasource.
     *
     * @param datasourceIdOrName datasource id or name
     * @return schema text
     */
    public String schema(String datasourceIdOrName) {
        DataSourceConfig datasource = requireEnabledDatasource(datasourceIdOrName);
        if (sqlPolicy.isHttpType(datasource.getType())) {
            return httpService.schemaHttp(datasource);
        }
        return jdbcService.schemaJdbc(datasource);
    }

    /**
     * Previews one datasource.
     *
     * @param datasourceIdOrName datasource id or name
     * @param limit preview row limit
     * @return preview text
     */
    public String preview(String datasourceIdOrName, int limit) {
        DataSourceConfig datasource = requireEnabledDatasource(datasourceIdOrName);
        if (sqlPolicy.isHttpType(datasource.getType())) {
            return httpService.previewHttp(datasource, limit);
        }
        return jdbcService.previewJdbc(datasource, limit);
    }

    /**
     * Executes a read-only SQL query on one datasource.
     *
     * @param datasourceIdOrName datasource id or name
     * @param sql read-only SQL
     * @param maxRows maximum rows
     * @return SQL result text
     */
    public String executeReadOnlySql(String datasourceIdOrName, String sql, int maxRows) {
        sqlPolicy.validateReadOnlySql(sql);
        DataSourceConfig datasource = requireEnabledDatasource(datasourceIdOrName);
        if (sqlPolicy.isHttpType(datasource.getType())) {
            return "该数据源不是数据库，不能执行 SQL。请使用 previewDataSource 预览 HTTP/JSON/CSV 数据。";
        }
        return jdbcService.executeReadOnlySql(datasource, sql, maxRows);
    }

    /**
     * Builds the datasource JDBC URL.
     *
     * @param datasource datasource configuration
     * @return JDBC URL
     */
    public String buildJdbcUrl(DataSourceConfig datasource) {
        return jdbcService.buildJdbcUrl(datasource);
    }
}
