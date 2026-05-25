package com.ai.security.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

public record AdminUpdateUserRolesRequest(
        @NotEmpty(message = "角色不能为空")
        Set<String> roles
) {
}
