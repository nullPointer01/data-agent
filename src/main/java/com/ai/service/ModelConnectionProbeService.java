package com.ai.service;

import com.ai.mcp.ModelClientFactory;
import com.ai.mcp.ModelEndpointResolver;
import com.ai.mcp.ModelProviderCatalog;
import com.ai.mcp.ResolvedModelEndpoint;
import com.ai.model.ModelConfig;
import com.ai.modelconfig.dto.ModelProbeRequest;
import com.ai.modelconfig.dto.ModelProbeResponse;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.ModelNotFoundException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.exception.UnresolvedModelServerException;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.ConnectException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

/** 使用短生命周期客户端执行一次最小真实 Chat 调用。 */
@Service
public class ModelConnectionProbeService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModelConnectionProbeService.class);
    private static final int PROBE_MAX_TOKENS = 32;
    private static final String PROBE_PROMPT = "Reply with OK.";

    private final ModelConfigService modelConfigService;
    private final ModelEndpointResolver endpointResolver;
    private final ModelClientFactory clientFactory;
    private final Duration timeout;

    public ModelConnectionProbeService(ModelConfigService modelConfigService,
            ModelEndpointResolver endpointResolver,
            ModelClientFactory clientFactory,
            @Value("${langchain4j.open-ai.timeout:30000}") Integer timeoutMillis) {
        this.modelConfigService = modelConfigService;
        this.endpointResolver = endpointResolver;
        this.clientFactory = clientFactory;
        this.timeout = Duration.ofMillis(timeoutMillis);
    }

    public ModelProbeResponse probe(ModelProbeRequest request) {
        long startedAt = System.nanoTime();
        ResolvedModelEndpoint endpoint = null;
        try {
            ModelConfig probeConfig = resolveProbeConfig(request);
            endpoint = endpointResolver.resolve(probeConfig);
            var model = clientFactory.createChatModel(endpoint, probeConfig.getTemperature(),
                    PROBE_MAX_TOKENS, timeout, false);
            ChatResponse response = model.chat(List.of(UserMessage.from(PROBE_PROMPT)));
            String finishReason = response.finishReason() == null ? null : response.finishReason().name();
            return ModelProbeResponse.success(elapsedMillis(startedAt), endpoint.providerKey(),
                    endpoint.baseUrl(), endpoint.modelName(), finishReason);
        } catch (Exception exception) {
            String errorCode = classify(exception);
            LOGGER.warn("Model connection probe failed | provider={} | errorCode={} | exception={}",
                    endpoint == null ? safeProvider(request == null ? null : request.provider()) : endpoint.providerKey(),
                    errorCode, exception.getClass().getSimpleName());
            return ModelProbeResponse.failure(errorCode, safeMessage(errorCode), elapsedMillis(startedAt),
                    endpoint == null ? null : endpoint.providerKey(),
                    endpoint == null ? null : endpoint.baseUrl(),
                    endpoint == null ? null : endpoint.modelName());
        }
    }

    private ModelConfig resolveProbeConfig(ModelProbeRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("探测请求不能为空");
        }
        ModelConfig existing = StringUtils.hasText(request.modelId())
                ? modelConfigService.getModel(request.modelId())
                : null;
        String provider = firstNonBlank(request.provider(), existing == null ? null : existing.getProvider());
        boolean reuseExisting = existing != null && sameProvider(provider, existing.getProvider());
        boolean reuseStoredSecret = reuseExisting
                && sameBaseUrl(provider, request.baseUrl(), existing.getBaseUrl());

        ModelConfig probe = new ModelConfig();
        probe.setProvider(provider);
        probe.setBaseUrl(firstNonBlank(request.baseUrl(), reuseExisting ? existing.getBaseUrl() : null));
        probe.setModelName(firstNonBlank(request.modelName(), reuseExisting ? existing.getModelName() : null));
        probe.setTemperature(request.temperature() != null ? request.temperature()
                : reuseExisting ? existing.getTemperature() : null);

        if (StringUtils.hasText(request.apiKey()) && !isMaskedSecret(request.apiKey())) {
            probe.setApiKey(request.apiKey().trim());
        } else if (reuseStoredSecret) {
            probe.setApiKey(existing.getApiKey());
        }
        return probe;
    }

    private String classify(Throwable exception) {
        if (hasCause(exception, AuthenticationException.class)
                || messageContains(exception, "api key 不能为空")) {
            return "AUTHENTICATION";
        }
        if (hasCause(exception, ModelNotFoundException.class)) {
            return "MODEL_NOT_FOUND";
        }
        if (hasCause(exception, RateLimitException.class)) {
            return "RATE_LIMITED";
        }
        if (hasCause(exception, TimeoutException.class)
                || hasCause(exception, java.net.http.HttpTimeoutException.class)) {
            return "TIMEOUT";
        }
        if (hasCause(exception, UnsupportedFeatureException.class)
                || hasCause(exception, InvalidRequestException.class)) {
            return "UNSUPPORTED";
        }
        if (hasCause(exception, UnresolvedModelServerException.class)
                || hasCause(exception, ConnectException.class)
                || hasCause(exception, IOException.class)) {
            return "NETWORK";
        }
        if (hasCause(exception, IllegalArgumentException.class)) {
            return "INVALID_ENDPOINT";
        }
        return "UNKNOWN";
    }

    private String safeMessage(String errorCode) {
        return switch (errorCode) {
            case "AUTHENTICATION" -> "认证失败，请使用目标端点自己签发且已启用的 API Key";
            case "MODEL_NOT_FOUND" -> "模型不存在或当前账号无权访问";
            case "RATE_LIMITED" -> "厂商限流或额度不足，请稍后重试";
            case "TIMEOUT" -> "连接超时，请检查网络和端点状态";
            case "NETWORK" -> "无法连接模型端点，请检查 Base URL 与网络";
            case "INVALID_ENDPOINT" -> "模型连接参数无效，请检查必填项和 Base URL";
            case "UNSUPPORTED" -> "该端点不支持当前 Chat 请求";
            default -> "连接测试失败，请检查配置或服务端日志";
        };
    }

    private boolean hasCause(Throwable exception, Class<? extends Throwable> type) {
        Throwable current = exception;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean messageContains(Throwable exception, String expected) {
        Throwable current = exception;
        while (current != null) {
            if (current.getMessage() != null
                    && current.getMessage().toLowerCase(Locale.ROOT).contains(expected)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean sameProvider(String first, String second) {
        if (!StringUtils.hasText(first) || !StringUtils.hasText(second)) {
            return false;
        }
        String firstKey = ModelProviderCatalog.resolve(first)
                .map(ModelProviderCatalog::key)
                .orElse(first.trim().toLowerCase(Locale.ROOT));
        String secondKey = ModelProviderCatalog.resolve(second)
                .map(ModelProviderCatalog::key)
                .orElse(second.trim().toLowerCase(Locale.ROOT));
        return firstKey.equals(secondKey);
    }

    private boolean isMaskedSecret(String secret) {
        return secret != null && secret.contains("****");
    }

    private boolean sameBaseUrl(String provider, String requestedBaseUrl, String existingBaseUrl) {
        if (!StringUtils.hasText(requestedBaseUrl)) {
            return true;
        }
        return normalizedBaseUrl(provider, requestedBaseUrl)
                .equals(normalizedBaseUrl(provider, existingBaseUrl));
    }

    private String normalizedBaseUrl(String provider, String baseUrl) {
        String resolved = StringUtils.hasText(baseUrl)
                ? baseUrl.trim()
                : ModelProviderCatalog.resolve(provider)
                        .map(ModelProviderCatalog::defaultBaseUrl)
                        .orElse("");
        while (resolved.endsWith("/")) {
            resolved = resolved.substring(0, resolved.length() - 1);
        }
        return resolved;
    }

    private String firstNonBlank(String primary, String fallback) {
        return StringUtils.hasText(primary) ? primary.trim() : fallback;
    }

    private long elapsedMillis(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    private String safeProvider(String provider) {
        return StringUtils.hasText(provider) ? provider.trim() : "-";
    }
}
