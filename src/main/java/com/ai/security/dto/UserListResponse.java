package com.ai.security.dto;

import java.util.List;

/** 平台用户管理的服务端分页响应，统计覆盖全部匹配账号。 */
public record UserListResponse(boolean success, List<UserAdminResponse> items,
        int page, int size, long total, long enabledCount, long adminCount) { }
