package com.ai.modelconfig.dto;

import java.util.List;

/**
 * Model configuration list response.
 *
 * @author data-agent
 */
public record ModelConfigListResponse(boolean success, List<ModelConfigResponse> models) {
}
