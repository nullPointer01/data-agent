package com.ai.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Set;

@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    private final SecretKey key;
    private final long accessTokenValidityMs;
    private final long refreshTokenValidityMs;

    public JwtTokenProvider(
            @Value("${jwt.secret:default-secret-key-must-be-at-least-256-bits-long-for-hs256}") String secret,
            @Value("${jwt.access-token-validity-ms:7200000}") long accessTokenValidityMs,
            @Value("${jwt.refresh-token-validity-ms:604800000}") long refreshTokenValidityMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
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
            builder.claim("roles", String.join(",", roles));
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
            log.warn("JWT token expired");
        } catch (JwtException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
        }
        return false;
    }
}
