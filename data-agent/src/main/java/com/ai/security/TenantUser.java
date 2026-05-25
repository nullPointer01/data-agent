package com.ai.security;

import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

/**
 * Authenticated principal carrying user, tenant and authority information.
 *
 * @author data-agent
 */
public class TenantUser {

    private final String userId;
    private final String username;
    private final String tenantId;
    private final Collection<? extends GrantedAuthority> authorities;

    public TenantUser(String userId, String username, String tenantId,
            Collection<? extends GrantedAuthority> authorities) {
        this.userId = userId;
        this.username = username;
        this.tenantId = tenantId;
        this.authorities = authorities;
    }

    public String getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getTenantId() {
        return tenantId;
    }

    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    public boolean hasRole(String role) {
        return authorities.stream()
                .anyMatch(authority -> authority.getAuthority().equals(SecurityConstants.ROLE_PREFIX + role));
    }

    public boolean isAdmin() {
        return hasRole(SecurityConstants.ROLE_ADMIN);
    }
}
