package com.ai.mcp;

import com.ai.model.ModelConfig;
import com.ai.security.OutboundUrlGuard;
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

/**
 * 厂商模型列表拉取客户端（GET /models）。
 *
 * <p>模型聊天调用已统一走 LangChain4j（见 {@link ModelClientRegistry}），
 * 本类仅承担 LangChain4j 未覆盖的管理功能：调用 OpenAI 兼容的模型列表接口，
 * 供配置页一键选择模型名。</p>
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
    private static final int HTTP_TOO_MANY_REQUESTS_STATUS = 429;
    private static final int HTTP_SERVER_ERROR_MIN_STATUS = 500;
    private static final int TRAILING_SLASH_TRIM_LENGTH = 1;
    private static final int API_KEY_VISIBLE_PREFIX_LENGTH = 4;
    private static final int API_KEY_VISIBLE_SUFFIX_LENGTH = 4;
    private static final int API_KEY_MASK_THRESHOLD = 8;
    private static final String KIMI_CODE_BASE_URL = "https://api.kimi.com/coding";
    private static final String KIMI_CODE_OPENAI_BASE_URL = "https://api.kimi.com/coding/v1";
    private static final String MODELS_PATH = "/models";
    private static final String NULL_OR_EMPTY = "null/empty";
    private static final String MASK_SEGMENT = "****";
    private static final String HEADER_ACCEPT = "Accept";
    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String AUTHORIZATION_BEARER_PREFIX = "Bearer ";
    private static final String URL_SEPARATOR = "/";

    private final ObjectMapper objectMapper;
    private final OutboundUrlGuard outboundUrlGuard;
    private final String defaultApiKey;
    private final String defaultBaseUrl;
    private final Integer defaultTimeout;
    private final HttpClient httpClient;

    public ModelHttpClient(ObjectMapper objectMapper,
            OutboundUrlGuard outboundUrlGuard,
            @Value("${langchain4j.open-ai.api-key:}") String defaultApiKey,
            @Value("${langchain4j.open-ai.base-url:}") String defaultBaseUrl,
            @Value("${langchain4j.open-ai.timeout:30000}") Integer defaultTimeout) {
        this.objectMapper = objectMapper;
        this.outboundUrlGuard = outboundUrlGuard;
        this.defaultApiKey = defaultApiKey;
        this.defaultBaseUrl = defaultBaseUrl;
        this.defaultTimeout = defaultTimeout;
        this.httpClient = buildHttpClient();
    }

    /**
     * 调用厂商的模型列表接口（GET /models），返回可用模型 ID 列表。
     *
     * <p>所有 OpenAI 兼容厂商均支持该端点；个别厂商（如部分讯飞、千帆版本）不支持时
     * 会抛出 HTTP 异常，由调用方提示用户手动填写模型名。</p>
     *
     * @param config 模型配置（需包含 provider/baseUrl/apiKey 其中可解析出地址与密钥）
     * @return 模型 ID 列表，按字母排序
     * @throws Exception 网络或认证异常
     */
    public List<String> listAvailableModels(ModelConfig config) throws Exception {
        String url = normalizeBaseUrl(resolveBaseUrl(config)) + MODELS_PATH;
        // baseUrl 来自用户输入，先做 SSRF 校验再发起请求
        outboundUrlGuard.assertSafe(url);
        String apiKey = resolveApiKey(config);
        LOGGER.info("Fetching available models | url={} | apiKeyPrefix={}", url, maskApiKey(apiKey));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header(HEADER_ACCEPT, CONTENT_TYPE_JSON)
                .header(HEADER_AUTHORIZATION, AUTHORIZATION_BEARER_PREFIX + apiKey)
                .GET()
                .timeout(Duration.ofMillis(defaultTimeout))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != HTTP_OK_STATUS) {
            throw buildHttpException(response.statusCode(), response.body(), config);
        }

        JsonNode data = objectMapper.readTree(response.body()).path("data");
        List<String> models = new ArrayList<>();
        if (data.isArray()) {
            for (JsonNode node : data) {
                String id = node.path("id").asText(null);
                if (id != null && !id.isBlank()) {
                    models.add(id);
                }
            }
        }
        Collections.sort(models);
        return models;
    }

    private ModelHttpException buildHttpException(int statusCode, String body, ModelConfig config) {
        String message = "HTTP " + statusCode + ": " + body;
        if (statusCode == HTTP_UNAUTHORIZED_STATUS || statusCode == HTTP_FORBIDDEN_STATUS) {
            message = "模型认证失败: API Key 无效、无权限，或与 Base URL 不匹配。"
                    + " provider=" + safe(config.getProvider())
                    + ", baseUrl=" + normalizeBaseUrl(resolveBaseUrl(config))
                    + ", detail=" + body;
        } else if (statusCode == HTTP_NOT_FOUND_STATUS) {
            message = "模型接口不存在: 请检查 Base URL。"
                    + " baseUrl=" + normalizeBaseUrl(resolveBaseUrl(config))
                    + ", detail=" + body;
        }
        return new ModelHttpException(statusCode, message, isRetriableStatus(statusCode));
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

    private String resolveBaseUrl(ModelConfig config) {
        if (config.getBaseUrl() != null && !config.getBaseUrl().isBlank()) {
            return config.getBaseUrl();
        }
        return ModelProviderCatalog.resolve(config.getProvider())
                .map(ModelProviderCatalog::defaultBaseUrl)
                .orElse(defaultBaseUrl);
    }

    private String normalizeBaseUrl(String baseUrl) {
        String resolvedBaseUrl = baseUrl;
        if (resolvedBaseUrl == null || resolvedBaseUrl.isEmpty()) {
            resolvedBaseUrl = defaultBaseUrl;
        }
        if (resolvedBaseUrl == null || resolvedBaseUrl.isEmpty()) {
            return "";
        }
        if (resolvedBaseUrl.endsWith(URL_SEPARATOR)) {
            resolvedBaseUrl = resolvedBaseUrl.substring(0, resolvedBaseUrl.length() - TRAILING_SLASH_TRIM_LENGTH);
        }
        // Kimi Coding 的开放端点带 /v1 后缀，补齐以兼容用户少填的情况
        if (KIMI_CODE_BASE_URL.equals(resolvedBaseUrl)) {
            return KIMI_CODE_OPENAI_BASE_URL;
        }
        return resolvedBaseUrl;
    }

    private String resolveApiKey(ModelConfig config) {
        String apiKey = config.getApiKey() != null ? config.getApiKey() : defaultApiKey;
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("模型 API Key 未配置: provider="
                    + safe(config.getProvider()) + ", model=" + safe(config.getModelName()));
        }
        return apiKey;
    }

    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() <= API_KEY_MASK_THRESHOLD) {
            return NULL_OR_EMPTY;
        }
        return apiKey.substring(0, API_KEY_VISIBLE_PREFIX_LENGTH)
                + MASK_SEGMENT
                + apiKey.substring(apiKey.length() - API_KEY_VISIBLE_SUFFIX_LENGTH);
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
