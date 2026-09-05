package com.ai.security.dto;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 角色响应。
 *
 * @author data-agent
 */
public record RoleResponse(
        String roleCode,
        String name,
        String description,
        boolean systemRole,
        boolean enabled,
        Set<String> permissions,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
