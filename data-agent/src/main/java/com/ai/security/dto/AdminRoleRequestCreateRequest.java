package com.ai.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 管理员角色申请提交参数。
 *
 * @author data-agent
 */
public record AdminRoleRequestCreateRequest(
        @NotBlank(message = "申请理由不能为空")
        @Size(max = 512, message = "申请理由不能超过512个字符")
        String reason
) {
}
