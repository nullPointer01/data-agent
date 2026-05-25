package com.ai.security;
import com.ai.security.auth.JwtTokenProvider;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtTokenProviderTest {

    private static final long ACCESS_TOKEN_VALIDITY_MS = 7200000L;

    private static final long REFRESH_TOKEN_VALIDITY_MS = 604800000L;

    @Test
    void shouldGenerateDevelopmentSecretWhenJwtSecretMissingOutsideProduction() {
        JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(
                "",
                ACCESS_TOKEN_VALIDITY_MS,
                REFRESH_TOKEN_VALIDITY_MS,
                new MockEnvironment());

        String token = jwtTokenProvider.createAccessToken("user-1", "super", "default", Set.of("USER"));

        assertTrue(jwtTokenProvider.validateToken(token));
    }

    @Test
    void shouldRejectMissingJwtSecretInProduction() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        assertThrows(IllegalArgumentException.class, () -> new JwtTokenProvider(
                "",
                ACCESS_TOKEN_VALIDITY_MS,
                REFRESH_TOKEN_VALIDITY_MS,
                environment));
    }

    @Test
    void shouldRejectShortJwtSecret() {
        assertThrows(IllegalArgumentException.class, () -> new JwtTokenProvider(
                "short-secret",
                ACCESS_TOKEN_VALIDITY_MS,
                REFRESH_TOKEN_VALIDITY_MS,
                new MockEnvironment()));
    }

    @Test
    void shouldAcceptConfiguredJwtSecret() {
        assertDoesNotThrow(() -> new JwtTokenProvider(
                "local-development-secret-with-more-than-thirty-two-characters",
                ACCESS_TOKEN_VALIDITY_MS,
                REFRESH_TOKEN_VALIDITY_MS,
                new MockEnvironment()));
    }
}
