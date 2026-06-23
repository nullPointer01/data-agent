package com.ai.modelconfig.dto;

import com.ai.mcp.ModelProviderCatalog;

import java.util.Arrays;
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
            String defaultBaseUrl,
            String defaultModelName) {
    }

    /**
     * 从厂商目录构建响应。
     *
     * @return 目录响应
     */
    public static ProviderCatalogResponse fromCatalog() {
        List<ProviderItem> items = Arrays.stream(ModelProviderCatalog.values())
                .map(entry -> new ProviderItem(entry.key(), entry.displayName(),
                        entry.defaultBaseUrl(), entry.defaultModelName()))
                .toList();
        return new ProviderCatalogResponse(true, items);
    }
}
