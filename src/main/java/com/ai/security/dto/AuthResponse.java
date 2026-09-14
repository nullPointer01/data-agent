package com.ai.security.dto;

import java.util.Set;

/** 登录或注册成功后返回的令牌与当前用户身份快照。 */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String userId,
        String username,
        String nickname,
        String tenantId,
        Set<String> roles
) {
}
