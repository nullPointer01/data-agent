package com.ai.modelconfig.dto;

import java.util.List;

/**
 * 个人 Agent 可选择的模型目录响应。
 *
 * @param success 是否查询成功
 * @param models 模型选项
 */
public record PersonalModelOptionsResponse(boolean success, List<PersonalModelOptionResponse> models) {
}
