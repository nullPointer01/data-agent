package com.ai.agent.tool;

import com.ai.service.connector.DataConnectorService;
import com.ai.service.connector.DataSourceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 数据源工具，提供 Schema 发现、数据预览和只读查询能力。
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
     * 列出租户下已启用且可供 Agent 使用的数据源。
     *
     * @return 模型可读的数据源列表
     */
    public String listDataSources() {
        return dataSourceService.listEnabledForAgent();
    }

    /**
     * 获取指定数据源的数据库 Schema 或 HTTP 数据结构。
     *
     * @param datasourceName 数据源名称或 ID
     * @return Schema 或预览文本
     */
    public String getDatabaseSchema(String datasourceName) {
        LOGGER.info("Agent getDatabaseSchema: datasourceNameLength={}", lengthOf(datasourceName));
        return dataConnectorService.schema(datasourceName);
    }

    /**
     * 在租户作用域下执行只读 SQL 查询。
     *
     * @param datasourceName 数据源名称或 ID
     * @param sql 只读 SQL 语句
     * @return 文本表格格式的查询结果
     */
    public String executeSql(String datasourceName, String sql) {
        LOGGER.info("Agent executeSql: datasourceNameLength={}, sqlLength={}", lengthOf(datasourceName),
                lengthOf(sql));
        return dataConnectorService.executeReadOnlySql(datasourceName, sql, DEFAULT_SQL_MAX_ROWS);
    }

    /**
     * 预览指定数据源的前几行数据或远程响应体。
     *
     * @param datasourceName 数据源名称或 ID
     * @return 预览结果
     */
    public String previewDataSource(String datasourceName) {
        LOGGER.info("Agent previewDataSource: datasourceNameLength={}", lengthOf(datasourceName));
        return dataConnectorService.preview(datasourceName, DEFAULT_PREVIEW_ROWS);
    }

    private int lengthOf(String value) {
        return value == null ? 0 : value.length();
    }
}
