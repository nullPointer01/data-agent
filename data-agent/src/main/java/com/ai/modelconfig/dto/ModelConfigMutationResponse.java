package com.ai.modelconfig.dto;

/**
 * Response for model configuration mutation commands.
 *
 * @author data-agent
 */
public record ModelConfigMutationResponse(
        boolean success,
        String message,
        String modelId,
        String name,
        Boolean enabled) {

    public static ModelConfigMutationResponse created(String modelId, String name) {
        return new ModelConfigMutationResponse(true, "模型添加成功", modelId, name, null);
    }

    public static ModelConfigMutationResponse updated(String modelId) {
        return new ModelConfigMutationResponse(true, "模型更新成功", modelId, null, null);
    }

    public static ModelConfigMutationResponse deleted() {
        return new ModelConfigMutationResponse(true, "模型删除成功", null, null, null);
    }

    public static ModelConfigMutationResponse toggled(String modelId, boolean enabled) {
        return new ModelConfigMutationResponse(true, "模型状态更新成功", modelId, null, enabled);
    }

    public static ModelConfigMutationResponse failure(String message) {
        return new ModelConfigMutationResponse(false, message, null, null, null);
    }
}
