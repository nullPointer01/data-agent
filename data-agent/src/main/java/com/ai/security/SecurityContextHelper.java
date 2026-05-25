package com.ai.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Reads authenticated tenant user details from Spring Security context.
 *
 * @author data-agent
 */
@Component
public class SecurityContextHelper {

    public TenantUser getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof TenantUser) {
            return (TenantUser) authentication.getPrincipal();
        }
        return null;
    }

    public String getCurrentUserId() {
        TenantUser user = getCurrentUser();
        return user != null ? user.getUserId() : null;
    }

    public String getCurrentTenantId() {
        TenantUser user = getCurrentUser();
        return user != null ? user.getTenantId() : null;
    }

    public String getCurrentUsername() {
        TenantUser user = getCurrentUser();
        return user != null ? user.getUsername() : null;
    }

    public boolean isAdmin() {
        TenantUser user = getCurrentUser();
        return user != null && user.isAdmin();
    }
}
