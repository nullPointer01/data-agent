package com.ai.rag.rerank;

import com.ai.rag.RerankerProperties;
import com.ai.security.OutboundUrlGuard;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 调用兼容 {@code POST /v1/rerank} 协议的外部 Cross-Encoder Provider。
 *
 * @author data-agent
 */
@Component
public class HttpCrossEncoderRerankProvider implements RerankProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpCrossEncoderRerankProvider.class);
    private static final String PROVIDER_NAME = "http-cross-encoder";
    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String HEADER_CONTENT_TYPE = "Content-Type";
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final int HTTP_OK_MIN = 200;
    private static final int HTTP_OK_MAX = 299;
    private static final int HTTP_UNAUTHORIZED = 401;
    private static final int HTTP_FORBIDDEN = 403;
    private static final int HTTP_TOO_MANY_REQUESTS = 429;
    private static final int HTTP_SERVER_ERROR_MIN = 500;

    private final RerankerProperties properties;
    private final ObjectMapper objectMapper;
    private final OutboundUrlGuard outboundUrlGuard;
    private final HttpClient httpClient;
    private int consecutiveFailures;
    private long circuitOpenUntilMillis;
    private boolean recoveryCallInProgress;

    public HttpCrossEncoderRerankProvider(RerankerProperties properties,
            ObjectMapper objectMapper,
            OutboundUrlGuard outboundUrlGuard) {
        properties.validate();
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.outboundUrlGuard = outboundUrlGuard;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getTimeout())
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    @Override
    public RerankResponse rerank(RerankRequest request) {
        if (request == null || request.candidates() == null || request.candidates().isEmpty()) {
            return new RerankResponse(List.of());
        }
        acquireCircuitPermission();
        for (int attempt = 1; attempt <= properties.getMaxAttempts(); attempt++) {
            try {
                RerankResponse response = executeHttp(request);
                validateResponseContract(response, request.candidates().size());
                recordSuccess();
                return response;
            } catch (RerankProviderException exception) {
                if (!exception.retriable() || attempt == properties.getMaxAttempts()) {
                    recordFailure();
                    throw exception;
                }
                long delayMillis = retryDelayMillis(attempt);
                LOGGER.warn("Reranker 调用将在退避后重试: provider={}, model={}, attempt={}/{}, delayMs={}, category={}",
                        providerName(), modelName(), attempt, properties.getMaxAttempts(), delayMillis,
                        exception.category());
                try {
                    sleep(delayMillis);
                } catch (RerankProviderException interrupted) {
                    recordFailure();
                    throw interrupted;
                }
            }
        }
        throw new RerankProviderException("UNKNOWN", "Reranker 调用未产生结果", false);
    }

    @Override
    public String providerName() {
        return PROVIDER_NAME;
    }

    @Override
    public String modelName() {
        return properties.getModelName();
    }

    private RerankResponse executeHttp(RerankRequest request) {
        URI endpoint;
        try {
            outboundUrlGuard.assertSafe(properties.getBaseUrl());
            endpoint = URI.create(properties.getBaseUrl());
        } catch (IllegalArgumentException exception) {
            throw new RerankProviderException("ENDPOINT", "Reranker 服务地址未通过安全校验", false, exception);
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(endpoint)
                .timeout(properties.getTimeout())
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody(request)));
        if (StringUtils.hasText(properties.getApiKey())) {
            builder.header(HEADER_AUTHORIZATION, "Bearer " + properties.getApiKey().trim());
        }
        try {
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            validateStatus(response.statusCode());
            return parseResponse(response.body());
        } catch (HttpTimeoutException exception) {
            throw new RerankProviderException("TIMEOUT", "Reranker 请求超时", true, exception);
        } catch (ConnectException exception) {
            throw new RerankProviderException("NETWORK", "Reranker 连接失败", true, exception);
        } catch (IOException exception) {
            throw new RerankProviderException("NETWORK", "Reranker 网络请求失败", true, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RerankProviderException("INTERRUPTED", "Reranker 请求被中断", false, exception);
        }
    }

    private String requestBody(RerankRequest request) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", properties.getModelName());
        root.put("query", request.query() == null ? "" : request.query());
        root.put("top_n", request.candidates().size());
        root.put("return_documents", false);
        ArrayNode documents = root.putArray("documents");
        request.candidates().forEach(candidate -> documents.add(limit(candidate.content())));
        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception exception) {
            throw new RerankProviderException("REQUEST", "Reranker 请求序列化失败", false, exception);
        }
    }

    private RerankResponse parseResponse(String body) {
        try {
            JsonNode results = objectMapper.readTree(body).path("results");
            if (!results.isArray()) {
                throw new RerankProviderException("RESPONSE", "Reranker 响应缺少 results 数组", false);
            }
            List<RerankScore> scores = new ArrayList<>();
            for (JsonNode result : results) {
                JsonNode index = result.get("index");
                JsonNode score = result.get("relevance_score");
                if (index == null || !index.canConvertToInt() || score == null || !score.isNumber()) {
                    throw new RerankProviderException("RESPONSE", "Reranker 响应结果字段非法", false);
                }
                scores.add(new RerankScore(index.intValue(), score.doubleValue()));
            }
            return new RerankResponse(List.copyOf(scores));
        } catch (RerankProviderException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new RerankProviderException("RESPONSE", "Reranker 响应不是有效 JSON", false, exception);
        }
    }

    private void validateStatus(int statusCode) {
        if (statusCode >= HTTP_OK_MIN && statusCode <= HTTP_OK_MAX) {
            return;
        }
        if (statusCode == HTTP_UNAUTHORIZED || statusCode == HTTP_FORBIDDEN) {
            throw new RerankProviderException("AUTHENTICATION", "Reranker 认证失败: HTTP " + statusCode, false);
        }
        if (statusCode == HTTP_TOO_MANY_REQUESTS) {
            throw new RerankProviderException("RATE_LIMIT", "Reranker 触发限流: HTTP 429", true);
        }
        if (statusCode >= HTTP_SERVER_ERROR_MIN) {
            throw new RerankProviderException("UPSTREAM", "Reranker 服务异常: HTTP " + statusCode, true);
        }
        throw new RerankProviderException("REQUEST", "Reranker 请求被拒绝: HTTP " + statusCode, false);
    }

    private void validateResponseContract(RerankResponse response, int expectedCount) {
        if (response == null || response.scores() == null || response.scores().size() != expectedCount) {
            throw new RerankProviderException("RESPONSE_CONTRACT", "Reranker 返回候选数量不完整", false);
        }
        Set<Integer> indexes = new HashSet<>();
        for (RerankScore score : response.scores()) {
            if (score == null || score.index() < 0 || score.index() >= expectedCount) {
                throw new RerankProviderException("RESPONSE_CONTRACT", "Reranker 返回越界候选索引", false);
            }
            if (!indexes.add(score.index())) {
                throw new RerankProviderException("RESPONSE_CONTRACT", "Reranker 返回重复候选索引", false);
            }
            if (!Double.isFinite(score.score())) {
                throw new RerankProviderException("RESPONSE_CONTRACT", "Reranker 返回非法相关性分数", false);
            }
        }
    }

    private synchronized void acquireCircuitPermission() {
        long now = System.currentTimeMillis();
        if (circuitOpenUntilMillis > now) {
            throw new RerankProviderException("CIRCUIT_OPEN", "Reranker 熔断器处于打开状态", false);
        }
        if (circuitOpenUntilMillis > 0L) {
            if (recoveryCallInProgress) {
                throw new RerankProviderException("CIRCUIT_OPEN", "Reranker 恢复探测正在执行", false);
            }
            recoveryCallInProgress = true;
        }
    }

    private synchronized void recordSuccess() {
        consecutiveFailures = 0;
        circuitOpenUntilMillis = 0L;
        recoveryCallInProgress = false;
    }

    private synchronized void recordFailure() {
        consecutiveFailures++;
        if (recoveryCallInProgress || consecutiveFailures >= properties.getCircuitFailureThreshold()) {
            circuitOpenUntilMillis = System.currentTimeMillis() + properties.getCircuitOpenDuration().toMillis();
            recoveryCallInProgress = false;
        }
    }

    private long retryDelayMillis(int completedAttempt) {
        long baseMillis = Math.max(1L, properties.getInitialBackoff().toMillis());
        int shift = completedAttempt - 1;
        return baseMillis > (Long.MAX_VALUE >> shift) ? Long.MAX_VALUE : baseMillis << shift;
    }

    private void sleep(long delayMillis) {
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RerankProviderException("INTERRUPTED", "Reranker 重试退避被中断", false, exception);
        }
    }

    private String limit(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= properties.getMaxDocumentChars()
                ? value
                : value.substring(0, properties.getMaxDocumentChars());
    }
}
