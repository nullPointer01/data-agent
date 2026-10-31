package com.ai.security;

import java.util.UUID;
import com.ai.security.rbac.SysRole;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * 持久化的系统用户，包含租户、角色和配额属性。
 *
 * @author data-agent
 */
public class SysUser {

    private static final long DEFAULT_DAILY_TOKEN_LIMIT = SecurityConstants.DEFAULT_USER_DAILY_TOKEN_LIMIT;

    private String id;

    private String username;

    private String password;

    private String nickname;

    private String email;

    private boolean enabled = true;

    private String tenantId;

    private Long dailyTokenLimit = DEFAULT_DAILY_TOKEN_LIMIT;

    private Set<SysRole> roles = new HashSet<>();

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public SysUser() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public long getDailyTokenLimit() {
        return dailyTokenLimit == null ? DEFAULT_DAILY_TOKEN_LIMIT : dailyTokenLimit;
    }

    public void setDailyTokenLimit(Long dailyTokenLimit) {
        this.dailyTokenLimit = dailyTokenLimit == null ? DEFAULT_DAILY_TOKEN_LIMIT : dailyTokenLimit;
    }

    public Set<SysRole> getRoles() {
        return roles;
    }

    public void setRoles(Set<SysRole> roles) {
        this.roles = roles == null ? new HashSet<>() : roles;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
