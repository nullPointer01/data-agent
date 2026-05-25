package com.ai.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminResetPasswordRequest(
        @NotBlank(message = "新密码不能为空")
        @Size(min = 6, max = 128, message = "密码长度必须在6到128位之间")
        String newPassword
) {
}
