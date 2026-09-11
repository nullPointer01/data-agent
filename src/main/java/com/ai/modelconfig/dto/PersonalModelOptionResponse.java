package com.ai.modelconfig.dto;

import com.ai.mcp.ModelProviderCatalog;
import com.ai.model.ModelConfig;

/**
 * 个人 Agent 使用的最小模型选项，不包含连接配置和凭据。
 *
 * @param modelId 模型配置 ID
 * @param displayName 展示名称
 * @param modelName 厂商模型名称
 * @param provider 厂商标识
 * @param isDefault 是否为默认模型
 */
public record PersonalModelOptionResponse(
        String modelId,
        String displayName,
        String modelName,
        String provider,
        boolean isDefault) {

    public static PersonalModelOptionResponse from(ModelConfig config) {
        return new PersonalModelOptionResponse(
                config.getModelId(),
                config.getName(),
                config.getModelName(),
                ModelProviderCatalog.resolve(config.getProvider())
                        .map(ModelProviderCatalog::key)
                        .orElse(config.getProvider()),
                config.isDefault());
    }
}
