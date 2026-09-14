package com.ai.security.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

/** 管理员全量替换某用户角色集合的请求。 */
public record AdminUpdateUserRolesRequest(
        @NotEmpty(message = "角色不能为空")
        Set<String> roles
) {
}
