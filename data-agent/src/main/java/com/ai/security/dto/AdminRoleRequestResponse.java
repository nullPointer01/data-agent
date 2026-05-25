package com.ai.security.dto;

import com.ai.security.rbac.AdminRoleRequestStatus;

import java.time.LocalDateTime;

/**
 * 管理员角色申请响应。
 *
 * @author data-agent
 */
public record AdminRoleRequestResponse(
        String requestId,
        String userId,
        String username,
        String tenantId,
        String reason,
        AdminRoleRequestStatus status,
        String reviewerId,
        String reviewerName,
        String reviewComment,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime reviewedAt
) {
}
