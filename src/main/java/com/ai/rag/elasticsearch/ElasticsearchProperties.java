package com.ai.rag.elasticsearch;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Elasticsearch 全文检索配置。
 *
 * @author data-agent
 */
@ConfigurationProperties(prefix = "app.rag.elasticsearch")
public class ElasticsearchProperties {

    private String baseUrl = "http://localhost:9200";

    private String indexName = "data-agent-rag-v3";

    private String analyzer = "rag_icu";

    private Duration contractCheckInterval = Duration.ofSeconds(30);

    public String getAnalyzer() { return analyzer; }
    public void setAnalyzer(String value) {
        if (value == null || !value.matches("[a-z][a-z0-9_]{0,63}") || value.equals("standard")) {
            throw new IllegalArgumentException("analyzer 必须是自定义 ICU 分析器名称");
        }
        analyzer = value;
    }
    public Duration getContractCheckInterval() { return contractCheckInterval; }
    public void setContractCheckInterval(Duration value) {
        if (value == null || value.isNegative() || value.compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalArgumentException("索引契约检查间隔必须在 0 到 1 小时之间");
        }
        contractCheckInterval = value;
    }

    private String username = "";

    private String password = "";

    private Duration connectTimeout = Duration.ofSeconds(2);

    private Duration requestTimeout = Duration.ofSeconds(5);

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getIndexName() {
        return indexName;
    }

    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }
}
