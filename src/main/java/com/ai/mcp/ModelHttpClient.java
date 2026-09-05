package com.ai.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

/**
 * OpenAI-compatible {@code GET /models} 管理客户端。
 *
 * @author data-agent
 */
@Component
public class ModelHttpClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModelHttpClient.class);
    private static final int DEFAULT_HTTP_CONNECT_TIMEOUT_SECONDS = 10;
    private static final int HTTP_OK_STATUS = 200;
    private static final int HTTP_UNAUTHORIZED_STATUS = 401;
    private static final int HTTP_FORBIDDEN_STATUS = 403;
    private static final int HTTP_NOT_FOUND_STATUS = 404;
    private static final int HTTP_METHOD_NOT_ALLOWED_STATUS = 405;
    private static final int HTTP_TOO_MANY_REQUESTS_STATUS = 429;
    private static final int HTTP_SERVER_ERROR_MIN_STATUS = 500;
    private static final String MODELS_PATH = "/models";
    private static final String HEADER_ACCEPT = "Accept";
    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String HEADER_CONTENT_TYPE = "Content-Type";
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String CONTENT_TYPE_HTML = "text/html";
    private static final String AUTHORIZATION_BEARER_PREFIX = "Bearer ";

    private final ObjectMapper objectMapper;
    private final Integer defaultTimeout;
    private final HttpClient httpClient;

    public ModelHttpClient(ObjectMapper objectMapper,
            @Value("${langchain4j.open-ai.timeout:30000}") Integer defaultTimeout) {
        this.objectMapper = objectMapper;
        this.defaultTimeout = defaultTimeout;
        this.httpClient = buildHttpClient();
    }

    public List<String> listAvailableModels(ResolvedModelEndpoint endpoint) throws Exception {
        String url = endpoint.baseUrl() + MODELS_PATH;
        LOGGER.info("Fetching available models | provider={} | url={}", endpoint.providerKey(), url);

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header(HEADER_ACCEPT, CONTENT_TYPE_JSON)
                .GET()
                .timeout(Duration.ofMillis(defaultTimeout));
        if (endpoint.hasApiKey()) {
            requestBuilder.header(HEADER_AUTHORIZATION, AUTHORIZATION_BEARER_PREFIX + endpoint.apiKey());
        }

        HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != HTTP_OK_STATUS) {
            throw buildHttpException(response.statusCode(), endpoint);
        }

        String responseBody = response.body();
        String contentType = response.headers().firstValue(HEADER_CONTENT_TYPE).orElse("");
        if (contentType.toLowerCase().contains(CONTENT_TYPE_HTML) || looksLikeHtml(responseBody)) {
            throw new ModelHttpException(HTTP_OK_STATUS,
                    "该地址返回的是网页，不是模型 API；请确认 Base URL 使用兼容接口根路径，通常以 /v1 结尾",
                    false);
        }

        JsonNode data;
        try {
            data = objectMapper.readTree(responseBody).path("data");
        } catch (Exception exception) {
            throw new ModelHttpException(HTTP_OK_STATUS,
                    "模型列表响应不是有效 JSON，请检查 Base URL 是否指向 OpenAI-compatible API",
                    false);
        }
        if (!data.isArray()) {
            throw new ModelHttpException(HTTP_OK_STATUS,
                    "模型列表响应缺少 data 数组，请手动填写模型 ID 或检查 Base URL",
                    false);
        }
        TreeSet<String> uniqueModels = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (JsonNode node : data) {
            String id = node.path("id").asText(null);
            if (id != null && !id.isBlank()) {
                uniqueModels.add(id.trim());
            }
        }
        return new ArrayList<>(uniqueModels);
    }

    private boolean looksLikeHtml(String body) {
        if (body == null) {
            return false;
        }
        String normalized = body.stripLeading().toLowerCase();
        return normalized.startsWith("<!doctype html") || normalized.startsWith("<html");
    }

    private ModelHttpException buildHttpException(int statusCode, ResolvedModelEndpoint endpoint) {
        String message = "模型列表请求失败: HTTP " + statusCode;
        if (statusCode == HTTP_UNAUTHORIZED_STATUS || statusCode == HTTP_FORBIDDEN_STATUS) {
            message = "模型认证失败，请使用目标端点自己签发且已启用的 API Key";
        } else if (statusCode == HTTP_NOT_FOUND_STATUS || statusCode == HTTP_METHOD_NOT_ALLOWED_STATUS) {
            message = "该端点不支持自动获取模型列表，请手动填写模型名";
        }
        return new ModelHttpException(statusCode,
                message + " (provider=" + endpoint.providerKey() + ", baseUrl=" + endpoint.baseUrl() + ")",
                isRetriableStatus(statusCode));
    }

    private boolean isRetriableStatus(int statusCode) {
        return statusCode == HTTP_TOO_MANY_REQUESTS_STATUS || statusCode >= HTTP_SERVER_ERROR_MIN_STATUS;
    }

    private HttpClient buildHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(DEFAULT_HTTP_CONNECT_TIMEOUT_SECONDS))
                .version(HttpClient.Version.HTTP_1_1)
                .proxy(new ProxySelector() {
                    @Override
                    public List<Proxy> select(URI uri) {
                        return Collections.singletonList(Proxy.NO_PROXY);
                    }

                    @Override
                    public void connectFailed(URI uri, SocketAddress socketAddress, IOException exception) {
                        LOGGER.debug("Proxy disabled, ignored connectFailed for uri={}", uri, exception);
                    }
                })
                .build();
    }
}
