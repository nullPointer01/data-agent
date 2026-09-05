package com.ai.security.dto;

import java.util.Set;

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
