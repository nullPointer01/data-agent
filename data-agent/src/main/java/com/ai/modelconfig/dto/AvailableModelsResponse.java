package com.ai.modelconfig.dto;

import java.util.List;

/**
 * 厂商可用模型列表响应。
 *
 * @param success 是否成功
 * @param message 失败原因
 * @param models 模型 ID 列表
 * @author data-agent
 */
public record AvailableModelsResponse(
        boolean success,
        String message,
        List<String> models) {

    public static AvailableModelsResponse success(List<String> models) {
        return new AvailableModelsResponse(true, null, models);
    }

    public static AvailableModelsResponse failure(String message) {
        return new AvailableModelsResponse(false, message, List.of());
    }
}
