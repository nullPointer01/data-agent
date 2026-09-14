package com.ai.security.dto;

import java.time.LocalDateTime;
import java.util.Set;

/** 管理控制台展示的用户账号、角色、状态与配额摘要。 */
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
