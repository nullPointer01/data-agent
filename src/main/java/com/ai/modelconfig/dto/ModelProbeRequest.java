package com.ai.modelconfig.dto;

/** 保存前的临时模型连接探测请求。 */
public record ModelProbeRequest(
        String modelId,
        String provider,
        String apiKey,
        String baseUrl,
        String modelName,
        Double temperature) {
}
