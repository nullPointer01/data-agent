package com.ai.security.dto;

/** 按已知归属 ID 解析展示名称，不加载密码、权限或全量用户列表。 */
public record UserIdentityResponse(String userId, String username, String nickname) { }
