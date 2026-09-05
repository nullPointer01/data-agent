package com.ai.security.dto;

import java.time.LocalDateTime;

/**
 * 权限响应。
 *
 * @author data-agent
 */
public record PermissionResponse(
        String permissionCode,
        String name,
        String description,
        String resourceType,
        String action,
        boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
