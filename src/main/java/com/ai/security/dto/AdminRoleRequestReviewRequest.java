package com.ai.security.dto;

import jakarta.validation.constraints.Size;

/**
 * 管理员角色申请审核参数。
 *
 * @author data-agent
 */
public record AdminRoleRequestReviewRequest(
        @Size(max = 512, message = "审核备注不能超过512个字符")
        String comment
) {
}
