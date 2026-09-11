package com.ai.conversation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 会话重命名请求。
 *
 * @param title 新的会话标题
 * @author data-agent
 */
public record SessionRenameRequest(
        @NotBlank(message = "会话标题不能为空")
        @Size(max = 80, message = "会话标题不能超过80个字符")
        String title) {
}
