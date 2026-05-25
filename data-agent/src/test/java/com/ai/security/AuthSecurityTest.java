package com.ai.security;
import com.ai.security.auth.JwtAuthenticationFilter;
import com.ai.security.auth.SecurityConfig;
import com.ai.security.auth.JwtTokenProvider;
import com.ai.security.auth.AuthResult;
import com.ai.security.auth.AuthService;
import com.ai.security.auth.AuthController;

import com.ai.config.RateLimitInterceptor;
import com.ai.logging.RequestCorrelationContext;
import com.ai.security.dto.AdminUpdateUserRolesRequest;
import com.ai.security.dto.AuthResponse;
import com.ai.security.dto.LoginRequest;
import com.ai.security.dto.UserAdminResponse;
import com.ai.service.RateLimitService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({AuthController.class, UserAdminController.class})
@Import({SecurityConfig.class, AuthSecurityTest.TestBeans.class})
class AuthSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private UserAdminService userAdminService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private RateLimitService rateLimitService;

    @Test
    void loginRemainsPublic() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenReturn(AuthResult.success(
                new AuthResponse("access-token", "refresh-token", "user-1", "super", "super", "default", Set.of("USER"))));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "super",
                                "password", "123456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"));
    }

    @Test
    void adminUserRoleUpdateRejectsAnonymousUsers() throws Exception {
        mockMvc.perform(put("/api/v1/admin/users/user-1/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "roles", Set.of("ADMIN")))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verifyNoInteractions(authService);
        verifyNoInteractions(userAdminService);
    }

    @Test
    @WithMockUser(roles = "USER")
    void adminUserRoleUpdateRejectsNonAdminUsers() throws Exception {
        mockMvc.perform(put("/api/v1/admin/users/user-1/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "roles", Set.of("ADMIN")))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(authService);
        verifyNoInteractions(userAdminService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminUserRoleUpdateAllowsAdminUsers() throws Exception {
        when(userAdminService.updateRoles(any(String.class), any(AdminUpdateUserRolesRequest.class))).thenReturn(AuthResult.success(
                new UserAdminResponse("user-1", "super", "super", null, "default", true, 0L, Set.of("ADMIN"), null, null)));

        mockMvc.perform(put("/api/v1/admin/users/user-1/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "roles", Set.of("ADMIN")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value("user-1"))
                .andExpect(jsonPath("$.data.roles[0]").value("ADMIN"));

        verify(userAdminService).updateRoles(any(String.class), any(AdminUpdateUserRolesRequest.class));
    }

    @Test
    void jwtAuthenticationFilterWritesAuthenticatedMdcContext() throws Exception {
        JwtTokenProvider provider = mock(JwtTokenProvider.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(provider);
        org.springframework.mock.web.MockHttpServletRequest request =
                new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/v1/analysis/analyze");
        org.springframework.mock.web.MockHttpServletResponse response =
                new org.springframework.mock.web.MockHttpServletResponse();
        Claims claims = mock(Claims.class);
        request.addHeader(SecurityConstants.AUTHORIZATION_HEADER, SecurityConstants.BEARER_TOKEN_PREFIX + "token");
        when(provider.validateToken("token")).thenReturn(true);
        when(provider.parseToken("token")).thenReturn(claims);
        when(claims.getSubject()).thenReturn("user-1");
        when(claims.get("username", String.class)).thenReturn("alice");
        when(claims.get("tenantId", String.class)).thenReturn("tenant-1");
        when(claims.get("roles", String.class)).thenReturn("USER");

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            org.junit.jupiter.api.Assertions.assertEquals("user-1",
                    MDC.get(RequestCorrelationContext.MDC_USER_ID));
            org.junit.jupiter.api.Assertions.assertEquals("alice",
                    MDC.get(RequestCorrelationContext.MDC_USERNAME));
            org.junit.jupiter.api.Assertions.assertEquals("tenant-1",
                    MDC.get(RequestCorrelationContext.MDC_TENANT_ID));
        });

        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @TestConfiguration
    static class TestBeans {
        @Bean
        SecurityContextHelper securityContextHelper() {
            return new SecurityContextHelper();
        }

        @Bean
        RateLimitInterceptor rateLimitInterceptor(RateLimitService rateLimitService,
                SecurityContextHelper securityContextHelper) {
            return new RateLimitInterceptor(rateLimitService, securityContextHelper, 20);
        }
    }
}
