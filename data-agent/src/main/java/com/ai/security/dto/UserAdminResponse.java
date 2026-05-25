package com.ai.security.dto;

import java.time.LocalDateTime;
import java.util.Set;

public record UserAdminResponse(
        String userId,
        String username,
        String nickname,
        String email,
        String tenantId,
        boolean enabled,
        long dailyTokenLimit,
        Set<String> roles,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
