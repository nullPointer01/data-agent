package com.ai.memory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 用户修正长期语义记忆的请求。
 *
 * @param content 修正后的记忆内容
 * @author data-agent
 */
public record SemanticMemoryUpdateRequest(
        @NotBlank(message = "记忆内容不能为空")
        @Size(max = 500, message = "记忆内容不能超过500个字符")
        String content) {
}
