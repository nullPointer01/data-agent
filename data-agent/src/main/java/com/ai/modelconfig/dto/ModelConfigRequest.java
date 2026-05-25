package com.ai.modelconfig.dto;

/**
 * Request payload for creating or updating model configuration.
 *
 * @author data-agent
 */
public record ModelConfigRequest(
        String name,
        String provider,
        String apiKey,
        String baseUrl,
        String modelName,
        Double temperature,
        Integer maxTokens,
        Boolean enabled,
        Boolean isDefault) {
}
