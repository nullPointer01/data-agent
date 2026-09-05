package com.ai.mcp;

import com.ai.model.ModelConfig;
import com.ai.security.OutboundUrlGuard;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 模型端点的统一解析与出站安全边界。
 *
 * @author data-agent
 */
@Component
public class ModelEndpointResolver {

    private static final String KIMI_CODE_BASE_URL = "https://api.kimi.com/coding";
    private static final String KIMI_CODE_OPENAI_BASE_URL = "https://api.kimi.com/coding/v1";

    private final OutboundUrlGuard outboundUrlGuard;

    public ModelEndpointResolver(OutboundUrlGuard outboundUrlGuard) {
        this.outboundUrlGuard = outboundUrlGuard;
    }

    public ResolvedModelEndpoint resolve(ModelConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("模型配置不能为空");
        }
        return resolve(config.getProvider(), config.getBaseUrl(), config.getModelName(), config.getApiKey(), true);
    }

    public ResolvedModelEndpoint resolve(String provider, String baseUrl, String modelName, String apiKey) {
        return resolve(provider, baseUrl, modelName, apiKey, true);
    }

    public ResolvedModelEndpoint resolveForDiscovery(String provider, String baseUrl, String apiKey) {
        return resolve(provider, baseUrl, null, apiKey, false);
    }

    private ResolvedModelEndpoint resolve(String provider, String baseUrl, String modelName, String apiKey,
            boolean modelNameRequired) {
        if (!StringUtils.hasText(provider)) {
            throw new IllegalArgumentException("模型供应商不能为空");
        }

        ModelProviderCatalog entry = ModelProviderCatalog.resolve(provider)
                .orElse(ModelProviderCatalog.CUSTOM);
        String resolvedBaseUrl = normalizeBaseUrl(firstNonBlank(baseUrl, entry.defaultBaseUrl()));
        if (!StringUtils.hasText(resolvedBaseUrl)) {
            throw new IllegalArgumentException("Base URL 不能为空");
        }
        outboundUrlGuard.assertSafe(resolvedBaseUrl);

        String resolvedModelName = trimToNull(firstNonBlank(modelName, entry.defaultModelName()));
        if (modelNameRequired && !StringUtils.hasText(resolvedModelName)) {
            throw new IllegalArgumentException("模型名不能为空");
        }

        String resolvedApiKey = trimToNull(apiKey);
        if (entry.apiKeyRequired() && !StringUtils.hasText(resolvedApiKey)) {
            throw new IllegalArgumentException("API Key 不能为空: provider=" + entry.key());
        }

        return new ResolvedModelEndpoint(entry.key(), entry.protocol(), resolvedBaseUrl, resolvedModelName,
                resolvedApiKey, entry.apiKeyRequired(), entry.modelDiscoverySupported());
    }

    private String normalizeBaseUrl(String baseUrl) {
        String normalized = trimToNull(baseUrl);
        if (normalized == null) {
            return null;
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (KIMI_CODE_BASE_URL.equals(normalized)) {
            return KIMI_CODE_OPENAI_BASE_URL;
        }
        return normalized;
    }

    private String firstNonBlank(String primary, String fallback) {
        return StringUtils.hasText(primary) ? primary : fallback;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
