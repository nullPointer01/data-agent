package com.ai.security.dto;

import java.util.List;

/** 平台角色分页与全部匹配角色的统计。 */
public record RoleListResponse(boolean success, List<RoleResponse> items,
        int page, int size, long total, long enabledCount, long systemCount) { }
