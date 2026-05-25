package com.ai.service.connector;

import com.ai.model.DataSourceConfig;
import org.springframework.stereotype.Component;

/**
 * Builds JDBC URLs for supported datasource types.
 *
 * @author data-agent
 */
@Component
public class DataConnectorJdbcUrlBuilder {

    private static final int DEFAULT_POSTGRESQL_PORT = 5432;
    private static final int DEFAULT_CLICKHOUSE_PORT = 8123;
    private static final int DEFAULT_MYSQL_PORT = 3306;
    private static final String DEFAULT_HOST = "localhost";
    private static final String DEFAULT_H2_DATABASE = "mem:data_agent";
    private static final String TYPE_POSTGRESQL = "postgresql";
    private static final String TYPE_CLICKHOUSE = "clickhouse";
    private static final String TYPE_H2 = "h2";

    private final DataConnectorSqlPolicy sqlPolicy;

    public DataConnectorJdbcUrlBuilder(DataConnectorSqlPolicy sqlPolicy) {
        this.sqlPolicy = sqlPolicy;
    }

    /**
     * Builds the JDBC URL for one datasource.
     *
     * @param datasource datasource definition
     * @return JDBC URL
     */
    public String buildJdbcUrl(DataSourceConfig datasource) {
        String type = sqlPolicy.normalizeType(datasource.getType());
        String host = datasource.getHost() != null ? datasource.getHost() : DEFAULT_HOST;
        String db = datasource.getDbName() != null ? datasource.getDbName() : "";
        return switch (type) {
            case TYPE_POSTGRESQL -> String.format("jdbc:postgresql://%s:%d/%s", host,
                    datasource.getPort() != null ? datasource.getPort() : DEFAULT_POSTGRESQL_PORT, db);
            case TYPE_CLICKHOUSE -> String.format("jdbc:clickhouse://%s:%d/%s", host,
                    datasource.getPort() != null ? datasource.getPort() : DEFAULT_CLICKHOUSE_PORT, db);
            case TYPE_H2 -> datasource.getHost() != null && datasource.getHost().startsWith("jdbc:")
                    ? datasource.getHost()
                    : "jdbc:h2:" + (db.isBlank() ? DEFAULT_H2_DATABASE : db);
            default -> String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true", host,
                    datasource.getPort() != null ? datasource.getPort() : DEFAULT_MYSQL_PORT, db);
        };
    }
}
