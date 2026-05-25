package com.ai.service.connector;

import com.ai.model.DataSourceConfig;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;

/**
 * Executes JDBC datasource checks, metadata discovery and read-only queries.
 *
 * @author data-agent
 */
@Component
public class DataConnectorJdbcService {

    private static final int JDBC_VALIDATION_TIMEOUT_SECONDS = 5;
    private static final int JDBC_QUERY_TIMEOUT_SECONDS = 10;
    private static final int MAX_PREVIEW_LIMIT = 100;
    private static final int MAX_SQL_ROWS = 500;
    private static final int MAX_TABLE_SCHEMA_COUNT = 50;
    private static final String DEFAULT_PREVIEW_PREFIX = "SELECT * FROM ";

    private final DataConnectorJdbcUrlBuilder jdbcUrlBuilder;
    private final DataConnectorSqlPolicy sqlPolicy;

    public DataConnectorJdbcService(DataConnectorJdbcUrlBuilder jdbcUrlBuilder, DataConnectorSqlPolicy sqlPolicy) {
        this.jdbcUrlBuilder = jdbcUrlBuilder;
        this.sqlPolicy = sqlPolicy;
    }

    /**
     * Tests a JDBC datasource connection.
     *
     * @param datasource datasource definition
     * @return textual health result
     */
    public String testJdbc(DataSourceConfig datasource) {
        try (Connection conn = DriverManager.getConnection(buildJdbcUrl(datasource), datasource.getUsername(),
                datasource.getPassword())) {
            return conn.isValid(JDBC_VALIDATION_TIMEOUT_SECONDS) ? "连接成功" : "连接失败";
        } catch (Exception e) {
            return "连接失败: " + e.getMessage();
        }
    }

    /**
     * Reads schema information for one JDBC datasource.
     *
     * @param datasource datasource definition
     * @return schema text
     */
    public String schemaJdbc(DataSourceConfig datasource) {
        try (Connection conn = DriverManager.getConnection(buildJdbcUrl(datasource), datasource.getUsername(),
                datasource.getPassword())) {
            return buildSchema(conn, datasource);
        } catch (Exception e) {
            return "获取 Schema 失败: " + e.getMessage();
        }
    }

    /**
     * Previews the first JDBC table.
     *
     * @param datasource datasource definition
     * @param limit preview row limit
     * @return preview text
     */
    public String previewJdbc(DataSourceConfig datasource, int limit) {
        try {
            String table = firstTable(datasource);
            if (table == null) {
                return "数据库中没有可预览的表";
            }
            return queryJdbc(datasource, DEFAULT_PREVIEW_PREFIX + table,
                    sqlPolicy.normalizeLimit(limit, MAX_PREVIEW_LIMIT));
        } catch (Exception e) {
            return "数据预览失败: " + e.getMessage();
        }
    }

    /**
     * Executes a read-only JDBC query with an upper row bound.
     *
     * @param datasource datasource definition
     * @param sql read-only SQL
     * @param maxRows maximum rows to return
     * @return query result text
     */
    public String executeReadOnlySql(DataSourceConfig datasource, String sql, int maxRows) {
        try {
            return queryJdbc(datasource, sql, maxRows);
        } catch (Exception e) {
            return "SQL 执行失败: " + e.getMessage();
        }
    }

    /**
     * Builds a JDBC URL for one datasource.
     *
     * @param datasource datasource definition
     * @return JDBC URL
     */
    public String buildJdbcUrl(DataSourceConfig datasource) {
        return jdbcUrlBuilder.buildJdbcUrl(datasource);
    }

    private String buildSchema(Connection conn, DataSourceConfig datasource) throws Exception {
        DatabaseMetaData meta = conn.getMetaData();
        String catalog = conn.getCatalog();
        StringBuilder sb = new StringBuilder();
        sb.append("数据库: ").append(datasource.getDbName()).append("（").append(datasource.getType()).append("）\n\n");
        int tableCount = 0;
        try (ResultSet tables = meta.getTables(catalog, null, "%", new String[]{"TABLE"})) {
            while (tables.next()) {
                tableCount++;
                String tableName = tables.getString("TABLE_NAME");
                sb.append("## 表: ").append(tableName).append("\n");
                try (ResultSet cols = meta.getColumns(catalog, null, tableName, "%")) {
                    while (cols.next()) {
                        sb.append("  - ").append(cols.getString("COLUMN_NAME"))
                                .append(" ").append(cols.getString("TYPE_NAME"))
                                .append("(").append(cols.getInt("COLUMN_SIZE")).append(")")
                                .append("YES".equals(cols.getString("IS_NULLABLE")) ? " 可空" : " 非空")
                                .append("\n");
                    }
                }
                sb.append("\n");
                if (tableCount >= MAX_TABLE_SCHEMA_COUNT) {
                    sb.append("...(表过多，已截断)");
                    break;
                }
            }
        }
        return tableCount == 0 ? "数据库中没有找到任何表（可能权限不足或库为空）" : sb.toString();
    }

    private String firstTable(DataSourceConfig datasource) throws Exception {
        try (Connection conn = DriverManager.getConnection(buildJdbcUrl(datasource), datasource.getUsername(),
                datasource.getPassword())) {
            try (ResultSet tables = conn.getMetaData().getTables(conn.getCatalog(), null, "%", new String[]{"TABLE"})) {
                return tables.next() ? tables.getString("TABLE_NAME") : null;
            }
        }
    }

    private String queryJdbc(DataSourceConfig datasource, String sql, int maxRows) throws Exception {
        sqlPolicy.validateReadOnlySql(sql);
        try (Connection conn = DriverManager.getConnection(buildJdbcUrl(datasource), datasource.getUsername(),
                datasource.getPassword())) {
            conn.setReadOnly(true);
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                stmt.setQueryTimeout(JDBC_QUERY_TIMEOUT_SECONDS);
                stmt.setMaxRows(sqlPolicy.normalizeLimit(maxRows, MAX_SQL_ROWS));
                try (ResultSet rs = stmt.executeQuery(sql)) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int cols = meta.getColumnCount();
                    StringBuilder sb = new StringBuilder();
                    for (int i = 1; i <= cols; i++) {
                        sb.append(meta.getColumnLabel(i));
                        if (i < cols) {
                            sb.append(" | ");
                        }
                    }
                    sb.append("\n").append("-".repeat(40)).append("\n");
                    int rows = 0;
                    while (rs.next()) {
                        for (int i = 1; i <= cols; i++) {
                            String value = rs.getString(i);
                            sb.append(value != null ? value : "NULL");
                            if (i < cols) {
                                sb.append(" | ");
                            }
                        }
                        sb.append("\n");
                        rows++;
                    }
                    sb.append("\n共 ").append(rows).append(" 行");
                    return sb.toString();
                }
            } finally {
                conn.rollback();
            }
        }
    }
}
