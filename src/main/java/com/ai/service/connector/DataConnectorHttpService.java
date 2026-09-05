package com.ai.service.connector;

import com.ai.model.DataSourceConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * 执行基于 HTTP 的数据源测试和预览。
 *
 * @author data-agent
 */
@Component
public class DataConnectorHttpService {

    private static final int HTTP_CONNECT_TIMEOUT_SECONDS = 5;
    private static final int HTTP_REQUEST_TIMEOUT_SECONDS = 10;
    private static final int DEFAULT_PREVIEW_LIMIT = 10;
    private static final int MAX_PREVIEW_LIMIT = 100;
    private static final int MAX_TEXT_PREVIEW_LENGTH = 4000;
    private static final String TYPE_JSON = "json";
    private static final String TYPE_CSV = "csv";

    private final ObjectMapper objectMapper;
    private final DataConnectorSqlPolicy sqlPolicy;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(HTTP_CONNECT_TIMEOUT_SECONDS))
            .build();

    public DataConnectorHttpService(ObjectMapper objectMapper, DataConnectorSqlPolicy sqlPolicy) {
        this.objectMapper = objectMapper;
        this.sqlPolicy = sqlPolicy;
    }

    /**
     * 测试 HTTP 数据源是否可达。
     *
     * @param datasource 数据源定义
     * @return 文本形式的健康检查结果
     */
    public String testHttp(DataSourceConfig datasource) {
        try {
            HttpResponse<String> response = sendHttpGet(datasource);
            return sqlPolicy.isSuccessfulHttp(response.statusCode())
                    ? "连接成功，HTTP " + response.statusCode()
                    : "连接失败，HTTP " + response.statusCode();
        } catch (Exception e) {
            return "连接失败: " + e.getMessage();
        }
    }

    /**
     * Returns a schema-style preview for HTTP datasources.
     *
     * @param datasource datasource definition
     * @return preview text
     */
    public String schemaHttp(DataSourceConfig datasource) {
        return previewHttp(datasource, DEFAULT_PREVIEW_LIMIT);
    }

    /**
     * Returns a preview for HTTP datasources.
     *
     * @param datasource datasource definition
     * @param limit preview row limit
     * @return preview text
     */
    public String previewHttp(DataSourceConfig datasource, int limit) {
        try {
            HttpResponse<String> response = sendHttpGet(datasource);
            if (!sqlPolicy.isSuccessfulHttp(response.statusCode())) {
                return "HTTP 请求失败: " + response.statusCode();
            }
            String body = response.body();
            if (body == null || body.isBlank()) {
                return "HTTP 数据为空";
            }
            String type = sqlPolicy.normalizeType(datasource.getType());
            if (TYPE_JSON.equals(type) || sqlPolicy.looksLikeJson(body)) {
                JsonNode json = objectMapper.readTree(body);
                String prettyJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
                return sqlPolicy.truncate(prettyJson, MAX_TEXT_PREVIEW_LENGTH);
            }
            if (TYPE_CSV.equals(type)) {
                List<String> lines = body.lines()
                        .limit((long) sqlPolicy.normalizeLimit(limit, MAX_PREVIEW_LIMIT) + 1L)
                        .toList();
                return String.join("\n", lines);
            }
            return sqlPolicy.truncate(body, MAX_TEXT_PREVIEW_LENGTH);
        } catch (Exception e) {
            return "数据预览失败: " + e.getMessage();
        }
    }

    private HttpResponse<String> sendHttpGet(DataSourceConfig datasource) throws Exception {
        String url = datasource.getHost();
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("HTTP 数据源需要在 host 字段配置 URL");
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(HTTP_REQUEST_TIMEOUT_SECONDS))
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
