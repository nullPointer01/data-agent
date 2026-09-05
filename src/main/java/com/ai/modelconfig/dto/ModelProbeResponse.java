package com.ai.modelconfig.dto;

/** 模型连接探测结果。模型正文与密钥不会进入响应。 */
public record ModelProbeResponse(
        boolean success,
        String status,
        String errorCode,
        String message,
        long latencyMs,
        String provider,
        String baseUrl,
        String modelName,
        String finishReason) {

    public static ModelProbeResponse success(long latencyMs, String provider, String baseUrl,
            String modelName, String finishReason) {
        return new ModelProbeResponse(true, "SUCCESS", null, "连接成功", latencyMs,
                provider, baseUrl, modelName, finishReason);
    }

    public static ModelProbeResponse failure(String errorCode, String message, long latencyMs,
            String provider, String baseUrl, String modelName) {
        return new ModelProbeResponse(false, "ERROR", errorCode, message, latencyMs,
                provider, baseUrl, modelName, null);
    }
}
