package com.ai.security;

import java.util.Set;

/**
 * Security module constants.
 *
 * @author data-agent
 */
public final class SecurityConstants {

    public static final String AUTHORIZATION_HEADER = "Authorization";

    public static final String BEARER_TOKEN_PREFIX = "Bearer ";

    public static final String ROLE_PREFIX = "ROLE_";

    public static final String DEFAULT_TENANT_ID = "default";

    public static final String ROLE_ADMIN = "ADMIN";

    public static final String ROLE_USER = "USER";

    public static final Set<String> BUILTIN_ROLES = Set.of(ROLE_ADMIN, ROLE_USER);

    public static final long DEFAULT_USER_DAILY_TOKEN_LIMIT = 0L;

    private SecurityConstants() {
    }
}
