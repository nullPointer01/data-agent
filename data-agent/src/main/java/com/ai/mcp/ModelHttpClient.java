package com.ai.mcp;

import com.ai.model.ModelConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * HTTP client for OpenAI-compatible chat completion APIs.
 *
 * @author data-agent
 */
@Component
public class ModelHttpClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModelHttpClient.class);
    private static final int DEFAULT_HTTP_CONNECT_TIMEOUT_SECONDS = 10;
    private static final int DEFAULT_STREAM_TIMEOUT_SECONDS = 120;
    private static final int HTTP_OK_STATUS = 200;
    private static final int HTTP_UNAUTHORIZED_STATUS = 401;
    private static final int HTTP_FORBIDDEN_STATUS = 403;
    private static final int HTTP_NOT_FOUND_STATUS = 404;
    private static final int HTTP_TOO_MANY_REQUESTS_STATUS = 429;
    private static final int HTTP_SERVER_ERROR_MIN_STATUS = 500;
    private static final int RESPONSE_PREVIEW_LENGTH = 200;
    private static final int SSE_DATA_PREFIX_LENGTH = 5;
    private static final int TRAILING_SLASH_TRIM_LENGTH = 1;
    private static final int API_KEY_VISIBLE_PREFIX_LENGTH = 4;
    private static final int API_KEY_VISIBLE_SUFFIX_LENGTH = 4;
    private static final int API_KEY_MASK_THRESHOLD = 8;
    private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    private static final String KIMI_DEFAULT_BASE_URL = "https://api.moonshot.cn/v1";
    private static final String KIMI_CODE_BASE_URL = "https://api.kimi.com/coding";
    private static final String KIMI_CODE_OPENAI_BASE_URL = "https://api.kimi.com/coding/v1";
    private static final String KIMI_DEFAULT_MODEL_NAME = "kimi-k2.6";
    private static final String KIMI_CODE_MODEL_NAME = "kimi-for-coding";
    private static final String PROVIDER_KIMI = "kimi";
    private static final String PROVIDER_MOONSHOT = "moonshot";
    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";
    private static final String NULL_OR_EMPTY = "null/empty";
    private static final String MASK_SEGMENT = "****";
    private static final String REQUEST_KEY_MODEL = "model";
    private static final String REQUEST_KEY_MESSAGES = "messages";
    private static final String REQUEST_KEY_ROLE = "role";
    private static final String REQUEST_KEY_CONTENT = "content";
    private static final String REQUEST_KEY_STREAM = "stream";
    private static final String REQUEST_KEY_TEMPERATURE = "temperature";
    private static final String REQUEST_KEY_MAX_TOKENS = "max_tokens";
    private static final String ROLE_USER = "user";
    private static final String HEADER_CONTENT_TYPE = "Content-Type";
    private static final String HEADER_ACCEPT = "Accept";
    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String CONTENT_TYPE_EVENT_STREAM = "text/event-stream";
    private static final String AUTHORIZATION_BEARER_PREFIX = "Bearer ";
    private static final String SSE_DATA_PREFIX = "data:";
    private static final String SSE_DONE = "[DONE]";
    private static final String URL_SEPARATOR = "/";
    private static final String JSON_CHOICES = "choices";
    private static final String JSON_DELTA = "delta";
    private static final String JSON_MESSAGE = "message";
    private static final String JSON_CONTENT = "content";

    private final ObjectMapper objectMapper;
    private final String defaultApiKey;
    private final String defaultModelName;
    private final String defaultBaseUrl;
    private final Double defaultTemperature;
    private final Integer defaultTimeout;
    private final HttpClient httpClient;

    public ModelHttpClient(ObjectMapper objectMapper,
            @Value("${langchain4j.open-ai.api-key:}") String defaultApiKey,
            @Value("${langchain4j.open-ai.model-name:}") String defaultModelName,
            @Value("${langchain4j.open-ai.base-url:}") String defaultBaseUrl,
            @Value("${langchain4j.open-ai.temperature:0.7}") Double defaultTemperature,
            @Value("${langchain4j.open-ai.timeout:30000}") Integer defaultTimeout) {
        this.objectMapper = objectMapper;
        this.defaultApiKey = defaultApiKey;
        this.defaultModelName = defaultModelName;
        this.defaultBaseUrl = defaultBaseUrl;
        this.defaultTemperature = defaultTemperature;
        this.defaultTimeout = defaultTimeout;
        this.httpClient = buildHttpClient();
    }

    /**
     * Builds default model config from application properties.
     *
     * @return model config
     */
    public ModelConfig defaultConfig() {
        ModelConfig config = new ModelConfig();
        config.setApiKey(defaultApiKey);
        config.setModelName(defaultModelName);
        config.setBaseUrl(defaultBaseUrl);
        config.setTemperature(defaultTemperature);
        return config;
    }

    /**
     * Calls a chat completion endpoint and returns the full response text.
     *
     * @param prompt prompt text
     * @param config model config
     * @return model response
     * @throws Exception call exception
     */
    public String call(String prompt, ModelConfig config) throws Exception {
        String jsonBody = objectMapper.writeValueAsString(buildRequestBody(prompt, config, false));
        String url = resolveChatCompletionsUrl(config);
        String apiKey = resolveApiKey(config);
        String modelName = resolveModelName(config);

        LOGGER.info("HTTP client fallback | url={} | model={} | bodyLength={} | apiKeyPrefix={}",
                url, modelName, jsonBody.length(), maskApiKey(apiKey));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .header(HEADER_ACCEPT, CONTENT_TYPE_JSON)
                .header(HEADER_AUTHORIZATION, AUTHORIZATION_BEARER_PREFIX + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(Duration.ofMillis(defaultTimeout))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        LOGGER.info("HTTP client response | status={} | bodyLength={} | bodyPreview={}",
                response.statusCode(), response.body().length(), preview(response.body()));

        if (response.statusCode() != HTTP_OK_STATUS) {
            throw buildHttpException(response.statusCode(), response.body(), config);
        }

        return extractContent(response.body());
    }

    /**
     * Calls a streaming chat completion endpoint.
     *
     * @param prompt prompt text
     * @param config model config
     * @param tokenConsumer stream token consumer
     * @return full response
     * @throws Exception call exception
     */
    public String callStreaming(String prompt, ModelConfig config, Consumer<String> tokenConsumer) throws Exception {
        String jsonBody = objectMapper.writeValueAsString(buildRequestBody(prompt, config, true));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(resolveChatCompletionsUrl(config)))
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .header(HEADER_ACCEPT, CONTENT_TYPE_EVENT_STREAM)
                .header(HEADER_AUTHORIZATION, AUTHORIZATION_BEARER_PREFIX + resolveApiKey(config))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(Duration.ofSeconds(DEFAULT_STREAM_TIMEOUT_SECONDS))
                .build();

        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != HTTP_OK_STATUS) {
            String errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
            throw buildHttpException(response.statusCode(), errorBody, config);
        }
        return readSseResponse(response.body(), tokenConsumer);
    }

    private ModelHttpException buildHttpException(int statusCode, String body, ModelConfig config) {
        String message = "HTTP " + statusCode + ": " + body;
        if (statusCode == HTTP_UNAUTHORIZED_STATUS || statusCode == HTTP_FORBIDDEN_STATUS) {
            message = "模型认证失败: API Key 无效、无权限，或与 Base URL 不匹配。"
                    + " provider=" + safe(config.getProvider())
                    + ", baseUrl=" + normalizeBaseUrl(resolveBaseUrl(config))
                    + ", model=" + resolveModelName(config)
                    + ", detail=" + body;
        } else if (statusCode == HTTP_NOT_FOUND_STATUS) {
            message = "模型接口或模型名不存在: 请检查 Base URL 与模型名。"
                    + " baseUrl=" + normalizeBaseUrl(resolveBaseUrl(config))
                    + ", model=" + resolveModelName(config)
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

    private Map<String, Object> buildRequestBody(String prompt, ModelConfig config, boolean stream) {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put(REQUEST_KEY_MODEL, resolveModelName(config));
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of(REQUEST_KEY_ROLE, ROLE_USER, REQUEST_KEY_CONTENT, prompt));
        requestBody.put(REQUEST_KEY_MESSAGES, messages);
        if (stream) {
            requestBody.put(REQUEST_KEY_STREAM, true);
        }
        if (config.getTemperature() != null) {
            requestBody.put(REQUEST_KEY_TEMPERATURE, config.getTemperature());
        }
        if (config.getMaxTokens() != null) {
            requestBody.put(REQUEST_KEY_MAX_TOKENS, config.getMaxTokens());
        }
        return requestBody;
    }

    private String readSseResponse(InputStream body, Consumer<String> tokenConsumer) throws IOException {
        StringBuilder fullResponse = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith(SSE_DATA_PREFIX)) {
                    continue;
                }
                String data = line.substring(SSE_DATA_PREFIX_LENGTH).trim();
                if (SSE_DONE.equals(data)) {
                    break;
                }
                appendSseDelta(data, fullResponse, tokenConsumer);
            }
        }
        return fullResponse.toString();
    }

    private void appendSseDelta(String data, StringBuilder fullResponse, Consumer<String> tokenConsumer) {
        try {
            JsonNode root = objectMapper.readTree(data);
            String delta = root.path(JSON_CHOICES).path(0).path(JSON_DELTA).path(JSON_CONTENT).asText(null);
            if (delta != null && !delta.isEmpty()) {
                fullResponse.append(delta);
                tokenConsumer.accept(delta);
            }
        } catch (Exception e) {
            LOGGER.debug("Ignoring non-json SSE line: {}", data);
        }
    }

    private String extractContent(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode choices = root.path(JSON_CHOICES);
        if (choices.isArray() && !choices.isEmpty()) {
            return choices.get(0).path(JSON_MESSAGE).path(JSON_CONTENT).asText("");
        }
        return "";
    }

    private String resolveChatCompletionsUrl(ModelConfig config) {
        return normalizeBaseUrl(resolveBaseUrl(config)) + CHAT_COMPLETIONS_PATH;
    }

    private String normalizeBaseUrl(String baseUrl) {
        String resolvedBaseUrl = baseUrl;
        if (resolvedBaseUrl == null || resolvedBaseUrl.isEmpty()) {
            resolvedBaseUrl = defaultBaseUrl;
        }
        if (resolvedBaseUrl == null || resolvedBaseUrl.isEmpty()) {
            resolvedBaseUrl = DEFAULT_BASE_URL;
        }
        if (resolvedBaseUrl.endsWith(URL_SEPARATOR)) {
            resolvedBaseUrl = resolvedBaseUrl.substring(0, resolvedBaseUrl.length() - TRAILING_SLASH_TRIM_LENGTH);
        }
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

    private String resolveModelName(ModelConfig config) {
        if (config.getModelName() != null && !config.getModelName().isBlank()) {
            if (isKimiCodeBaseUrl(config.getBaseUrl()) && isKimiCodeModelAlias(config.getModelName())) {
                return KIMI_CODE_MODEL_NAME;
            }
            return config.getModelName();
        }
        if (isKimiCodeBaseUrl(config.getBaseUrl())) {
            return KIMI_CODE_MODEL_NAME;
        }
        if (isKimiProvider(config.getProvider())) {
            return KIMI_DEFAULT_MODEL_NAME;
        }
        return defaultModelName;
    }

    private String resolveBaseUrl(ModelConfig config) {
        if (config.getBaseUrl() != null && !config.getBaseUrl().isBlank()) {
            return config.getBaseUrl();
        }
        if (isKimiProvider(config.getProvider())) {
            return KIMI_DEFAULT_BASE_URL;
        }
        return defaultBaseUrl;
    }

    private boolean isKimiProvider(String provider) {
        String normalizedProvider = provider == null ? "" : provider.toLowerCase(Locale.ROOT);
        return PROVIDER_KIMI.equals(normalizedProvider) || PROVIDER_MOONSHOT.equals(normalizedProvider);
    }

    private boolean isKimiCodeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return false;
        }
        String normalizedBaseUrl = normalizeBaseUrl(baseUrl).toLowerCase(Locale.ROOT);
        return normalizedBaseUrl.equals(KIMI_CODE_OPENAI_BASE_URL)
                || normalizedBaseUrl.startsWith(KIMI_CODE_OPENAI_BASE_URL + URL_SEPARATOR);
    }

    private boolean isKimiCodeModelAlias(String modelName) {
        String normalizedModelName = modelName.toLowerCase(Locale.ROOT).replace("_", "-");
        return "kimi-2.6".equals(normalizedModelName)
                || "kimi-k2.6".equals(normalizedModelName)
                || "kimi-k2-6".equals(normalizedModelName)
                || "kimi-k2.6-code-preview".equals(normalizedModelName);
    }

    private String preview(String responseBody) {
        return responseBody.substring(0, Math.min(RESPONSE_PREVIEW_LENGTH, responseBody.length()));
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
