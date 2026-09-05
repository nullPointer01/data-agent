package com.ai.security.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record AdminUpdateUserRequest(
        @Size(max = 64, message = "昵称不能超过64个字符")
        String nickname,

        @Email(message = "邮箱格式不正确")
        @Size(max = 128, message = "邮箱不能超过128个字符")
        String email,

        @Size(max = 64, message = "租户ID不能超过64个字符")
        String tenantId,

        Boolean enabled,

        Long dailyTokenLimit
) {
}
