package com.ai.modelconfig.dto;

import com.ai.mcp.ModelProviderCatalog;

import java.util.List;

/**
 * 模型厂商目录响应：前端配置页的厂商下拉与默认值来源。
 *
 * @param success 是否成功
 * @param providers 厂商列表
 * @author data-agent
 */
public record ProviderCatalogResponse(
        boolean success,
        List<ProviderItem> providers) {

    /**
     * 单个厂商条目。
     *
     * @param key 厂商标识
     * @param label 显示名
     * @param defaultBaseUrl 默认接口地址
     * @param defaultModelName 默认模型名
     */
    public record ProviderItem(
            String key,
            String label,
            String group,
            String protocol,
            String defaultBaseUrl,
            String defaultModelName,
            List<String> recommendedModels,
            boolean apiKeyRequired,
            boolean modelDiscoverySupported) {
    }

    /**
     * 从厂商目录构建响应。
     *
     * @return 目录响应
     */
    public static ProviderCatalogResponse fromCatalog() {
        List<ProviderItem> items = java.util.Arrays.stream(ModelProviderCatalog.values())
                .map(entry -> new ProviderItem(entry.key(), entry.displayName(), entry.group().name(),
                        entry.protocol().name(), entry.defaultBaseUrl(), entry.defaultModelName(),
                        entry.recommendedModels(), entry.apiKeyRequired(), entry.modelDiscoverySupported()))
                .toList();
        return new ProviderCatalogResponse(true, items);
    }
}
