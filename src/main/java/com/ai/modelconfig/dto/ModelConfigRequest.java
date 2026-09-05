package com.ai.modelconfig.dto;

/**
 * 创建或更新模型配置的请求负载。
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
