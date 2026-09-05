package com.ai.modelconfig.dto;

/**
 * 模型配置详情响应。
 *
 * @author data-agent
 */
public record ModelConfigDetailResponse(
        boolean success,
        String message,
        ModelConfigResponse model) {

    public static ModelConfigDetailResponse success(ModelConfigResponse model) {
        return new ModelConfigDetailResponse(true, null, model);
    }

    public static ModelConfigDetailResponse failure(String message) {
        return new ModelConfigDetailResponse(false, message, null);
    }
}
