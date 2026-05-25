package com.ai.security.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 首个管理员初始化参数。
 *
 * @author data-agent
 */
public record BootstrapAdminRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(max = 64, message = "用户名不能超过64个字符")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 128, message = "密码长度必须在6到128位之间")
        String password,

        @Size(max = 64, message = "昵称不能超过64个字符")
        String nickname,

        @Email(message = "邮箱格式不正确")
        @Size(max = 128, message = "邮箱不能超过128个字符")
        String email,

        @Size(max = 64, message = "租户ID不能超过64个字符")
        String tenantId
) {
}
