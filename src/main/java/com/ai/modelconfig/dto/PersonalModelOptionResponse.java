package com.ai.modelconfig.dto;

import com.ai.mcp.ModelProviderCatalog;

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

    /** 统一旧厂商别名，SQL 投影始终不含凭据。 */
    public PersonalModelOptionResponse normalized() {
        return new PersonalModelOptionResponse(
                modelId, displayName, modelName,
                ModelProviderCatalog.resolve(provider)
                        .map(ModelProviderCatalog::key)
                        .orElse(provider), isDefault);
    }
}
