package com.ai.security.dto;

import jakarta.validation.constraints.NotBlank;

/** 用刷新令牌换取新访问令牌的请求。 */
public record RefreshTokenRequest(
        @NotBlank(message = "刷新令牌不能为空")
        String refreshToken
) {
}
