package com.ai.vector;

import java.util.Locale;

/**
 * 描述 Embedding 模型与向量索引之间不可混用的身份契约。
 *
 * @author data-agent
 */
public record EmbeddingProfile(
        String provider,
        String modelId,
        String indexVersion,
        int dimension,
        boolean normalize,
        String metric) {

    public EmbeddingProfile {
        provider = requireText(provider, "provider").toLowerCase(Locale.ROOT);
        modelId = requireText(modelId, "modelId");
        indexVersion = requireText(indexVersion, "indexVersion");
        metric = requireText(metric, "metric").toUpperCase(Locale.ROOT);
        if (dimension <= 0) {
            throw new IllegalArgumentException("Embedding dimension 必须大于 0: " + dimension);
        }
    }

    /**
     * 返回适合日志和诊断信息使用的稳定身份描述。
     *
     * @return 不包含凭据的模型身份
     */
    public String identity() {
        return provider + ":" + modelId + ":" + indexVersion + ":d" + dimension
                + ":" + metric + ":normalize=" + normalize;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Embedding Profile 字段不能为空: " + fieldName);
        }
        return value.trim();
    }
}
