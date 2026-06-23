package com.ai.service.connector;

import com.ai.model.DataSourceConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 为 JDBC 数据源查询提供按 (url|user) 复用的 HikariCP 连接池。
 *
 * <p>原实现每次查询都 {@code DriverManager.getConnection}，每次都做一次 TCP + 认证握手。
 * 这里按数据源缓存连接池，使突发/重复查询在同一数据源内复用物理连接。</p>
 *
 * <p>注意：这些是用户配置的外部库，因此 {@code minIdle=0}：池在空闲一段时间后回收到 0 连接，
 * 安静时表现等同按需连接、不对外部库长期占用空闲连接；只在突发期内复用，兼顾性能与外部库友好。</p>
 *
 * @author data-agent
 */
@Component
public class JdbcConnectionPoolManager {

    private static final int MAX_POOLS = 20;
    private static final int POOL_MAX_SIZE = 5;
    private static final int POOL_MIN_IDLE = 0;
    private static final long IDLE_TIMEOUT_MS = 60_000L;
    private static final long MAX_LIFETIME_MS = 300_000L;
    private static final long CONNECTION_TIMEOUT_MS = 10_000L;

    private final DataConnectorJdbcUrlBuilder jdbcUrlBuilder;

    // 访问序 LRU：超过上限淘汰最久未用的池并关闭，避免对外部库累积过多连接池
    private final Map<String, HikariDataSource> pools =
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, HikariDataSource> eldest) {
                    if (size() > MAX_POOLS) {
                        eldest.getValue().close();
                        return true;
                    }
                    return false;
                }
            };

    public JdbcConnectionPoolManager(DataConnectorJdbcUrlBuilder jdbcUrlBuilder) {
        this.jdbcUrlBuilder = jdbcUrlBuilder;
    }

    /**
     * 取得指定数据源的连接，连接来自按 (url|user) 复用的连接池。
     *
     * @param datasource 数据源定义
     * @return 物理连接，调用方负责关闭以归还连接池
     * @throws SQLException 获取连接失败
     */
    public Connection getConnection(DataSourceConfig datasource) throws SQLException {
        String url = jdbcUrlBuilder.buildJdbcUrl(datasource);
        String key = url + "|" + datasource.getUsername();
        HikariDataSource dataSource;
        // 同步保护 LinkedHashMap 的访问序与淘汰，computeIfAbsent 内部仅做轻量建池
        synchronized (pools) {
            dataSource = pools.computeIfAbsent(key, k -> createPool(url, datasource));
        }
        return dataSource.getConnection();
    }

    private HikariDataSource createPool(String url, DataSourceConfig datasource) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(datasource.getUsername());
        config.setPassword(datasource.getPassword());
        config.setMaximumPoolSize(POOL_MAX_SIZE);
        config.setMinimumIdle(POOL_MIN_IDLE);
        config.setIdleTimeout(IDLE_TIMEOUT_MS);
        config.setMaxLifetime(MAX_LIFETIME_MS);
        config.setConnectionTimeout(CONNECTION_TIMEOUT_MS);
        // 池级只读，与上层只读 SQL 策略一致，避免误写外部库
        config.setReadOnly(true);
        config.setPoolName("ds-" + Integer.toHexString(key(url, datasource)));
        return new HikariDataSource(config);
    }

    private int key(String url, DataSourceConfig datasource) {
        return (url + "|" + datasource.getUsername()).hashCode();
    }

    /**
     * 应用关闭时释放全部连接池。
     */
    @PreDestroy
    public void closeAll() {
        synchronized (pools) {
            pools.values().forEach(HikariDataSource::close);
            pools.clear();
        }
    }
}
