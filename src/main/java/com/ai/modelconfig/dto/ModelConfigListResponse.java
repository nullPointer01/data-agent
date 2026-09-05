package com.ai.modelconfig.dto;

import java.util.List;

/**
 * 模型配置列表响应。
 *
 * @author data-agent
 */
public record ModelConfigListResponse(boolean success, List<ModelConfigResponse> models) {
}
