package com.ai.modelconfig.dto;

import com.ai.model.ModelConfig;
import com.ai.util.CryptoUtil;

import java.time.LocalDateTime;

/**
 * Model configuration response.
 *
 * @author data-agent
 */
public record ModelConfigResponse(
        String modelId,
        String name,
        String provider,
        String apiKey,
        String baseUrl,
        String modelName,
        Double temperature,
        Integer maxTokens,
        boolean enabled,
        boolean isDefault,
        String tenantId,
        String createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static ModelConfigResponse from(ModelConfig config) {
        return new ModelConfigResponse(
                config.getModelId(),
                config.getName(),
                config.getProvider(),
                CryptoUtil.maskApiKey(config.getApiKey()),
                config.getBaseUrl(),
                config.getModelName(),
                config.getTemperature(),
                config.getMaxTokens(),
                config.isEnabled(),
                config.isDefault(),
                config.getTenantId(),
                config.getCreatedBy(),
                config.getCreatedAt(),
                config.getUpdatedAt());
    }
}
