package com.ai.security.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Creates and validates JWT access and refresh tokens.
 *
 * @author data-agent
 */
@Component
public class JwtTokenProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtTokenProvider.class);

    private static final int MIN_SECRET_LENGTH = 32;

    private static final int SECRET_BYTES_LENGTH = 32;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final SecretKey key;

    private final long accessTokenValidityMs;

    private final long refreshTokenValidityMs;

    public JwtTokenProvider(
            @Value("${jwt.secret:}") String secret,
            @Value("${jwt.access-token-validity-ms:7200000}") long accessTokenValidityMs,
            @Value("${jwt.refresh-token-validity-ms:604800000}") long refreshTokenValidityMs,
            Environment environment) {
        String resolvedSecret = resolveSecret(secret, environment);
        this.key = Keys.hmacShaKeyFor(resolvedSecret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenValidityMs = accessTokenValidityMs;
        this.refreshTokenValidityMs = refreshTokenValidityMs;
    }

    public String createAccessToken(String userId, String username, String tenantId, Set<String> roles) {
        return createToken(userId, username, tenantId, roles, accessTokenValidityMs);
    }

    public String createRefreshToken(String userId, String username, String tenantId) {
        return createToken(userId, username, tenantId, null, refreshTokenValidityMs);
    }

    private String createToken(String userId, String username, String tenantId, Set<String> roles, long validityMs) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + validityMs);

        JwtBuilder builder = Jwts.builder()
                .subject(userId)
                .issuedAt(now)
                .expiration(expiry)
                .claim("username", username)
                .claim("tenantId", tenantId);

        if (roles != null && !roles.isEmpty()) {
            builder.claim("roles", buildRolesClaim(roles));
        }

        return builder.signWith(key).compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String getUserId(String token) {
        return parseToken(token).getSubject();
    }

    public String getUsername(String token) {
        return parseToken(token).get("username", String.class);
    }

    public String getTenantId(String token) {
        return parseToken(token).get("tenantId", String.class);
    }

    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (ExpiredJwtException e) {
            LOGGER.warn("JWT token expired");
        } catch (JwtException e) {
            LOGGER.warn("Invalid JWT token: {}", e.getMessage());
        }
        return false;
    }

    private String resolveSecret(String secret, Environment environment) {
        if (StringUtils.hasText(secret)) {
            validateSecret(secret);
            return secret;
        }

        if (isProductionProfile(environment)) {
            throw new IllegalArgumentException("jwt.secret must be configured and contain at least 32 characters");
        }

        LOGGER.warn("jwt.secret is not configured. Generated an in-memory development JWT secret; "
                + "tokens will be invalid after application restart. Configure JWT_SECRET for shared environments.");
        return generateDevelopmentSecret();
    }

    private void validateSecret(String secret) {
        if (secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalArgumentException("jwt.secret must be configured and contain at least 32 characters");
        }
    }

    private boolean isProductionProfile(Environment environment) {
        return environment.acceptsProfiles(Profiles.of("prod", "production"));
    }

    private String generateDevelopmentSecret() {
        byte[] secretBytes = new byte[SECRET_BYTES_LENGTH];
        SECURE_RANDOM.nextBytes(secretBytes);
        return Base64.getEncoder().encodeToString(secretBytes);
    }

    private String buildRolesClaim(Set<String> roles) {
        StringJoiner joiner = new StringJoiner(",");
        roles.forEach(joiner::add);
        return joiner.toString();
    }
}
